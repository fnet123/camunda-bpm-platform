/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.migration.batch;

import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.impl.batch.AbstractBatchJobHandler;
import org.camunda.bpm.engine.impl.batch.BatchEntity;
import org.camunda.bpm.engine.impl.batch.BatchJobConfiguration;
import org.camunda.bpm.engine.impl.batch.BatchJobContext;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobDeclaration;
import org.camunda.bpm.engine.impl.json.MigrationBatchConfigurationJsonConverter;
import org.camunda.bpm.engine.impl.migration.MigrationPlanExecutionBuilderImpl;
import org.camunda.bpm.engine.impl.persistence.entity.*;
import org.camunda.bpm.engine.migration.MigrationPlan;
import org.camunda.bpm.engine.migration.MigrationPlanExecutionBuilder;

import java.util.List;

/**
 * Job handler for batch migration jobs. The batch migration job
 * migrates a list of process instances.
 */
public class MigrationBatchJobHandler extends AbstractBatchJobHandler<MigrationBatchConfiguration> {

  public static final MigrationBatchJobDeclaration JOB_DECLARATION = new MigrationBatchJobDeclaration();

  public String getType() {
    return Batch.TYPE_PROCESS_INSTANCE_MIGRATION;
  }

  public JobDeclaration<?, MessageEntity> getJobDeclaration() {
    return JOB_DECLARATION;
  }

  protected MigrationBatchConfigurationJsonConverter getJsonConverterInstance() {
    return MigrationBatchConfigurationJsonConverter.INSTANCE;
  }

  public boolean createJobs(BatchEntity batch) {
    CommandContext commandContext = Context.getCommandContext();
    ByteArrayManager byteArrayManager = commandContext.getByteArrayManager();
    JobManager jobManager = commandContext.getJobManager();

    MigrationBatchConfiguration configuration = readConfiguration(batch.getConfigurationBytes());
    MigrationPlan migrationPlan = configuration.getMigrationPlan();
    String sourceDeploymentId = getProcessDefinition(commandContext, migrationPlan.getSourceProcessDefinitionId()).getDeploymentId();

    int batchJobsPerSeed = batch.getBatchJobsPerSeed();
    int invocationsPerBatchJob = batch.getInvocationsPerBatchJob();

    List<String> processInstanceIds = configuration.getProcessInstanceIds();
    int numberOfInstancesToProcess = Math.min(invocationsPerBatchJob * batchJobsPerSeed, processInstanceIds.size());
    // view of process instances to process
    List<String> processInstancesToProcess = processInstanceIds.subList(0, numberOfInstancesToProcess);

    int createdJobs = 0;
    while (!processInstancesToProcess.isEmpty()) {
      int lastIdIndex = Math.min(invocationsPerBatchJob, processInstancesToProcess.size());
      // view of process instances for this job
      List<String> idsForJob = processInstancesToProcess.subList(0, lastIdIndex);

      MigrationBatchConfiguration jobConfiguration = MigrationBatchConfiguration
          .create(migrationPlan, idsForJob, configuration.isSkipCustomListeners(), configuration.isSkipIoMappings());
      ByteArrayEntity configurationEntity = saveConfiguration(byteArrayManager, jobConfiguration);

      JobEntity job = createBatchJob(batch, configurationEntity);
      job.setDeploymentId(sourceDeploymentId);
      jobManager.insertAndHintJobExecutor(job);

      idsForJob.clear();
      createdJobs++;
    }

    // update created jobs for batch
    batch.setJobsCreated(batch.getJobsCreated() + createdJobs);

    // update batch configuration
    batch.setConfigurationBytes(writeConfiguration(configuration));

    return processInstanceIds.isEmpty();
  }

  @Override
  public void execute(BatchJobConfiguration configuration, ExecutionEntity execution, CommandContext commandContext, String tenantId) {
    ByteArrayEntity configurationEntity = commandContext
        .getDbEntityManager()
        .selectById(ByteArrayEntity.class, configuration.getConfigurationByteArrayId());

    MigrationBatchConfiguration batchConfiguration = readConfiguration(configurationEntity.getBytes());

    MigrationPlanExecutionBuilder executionBuilder = commandContext.getProcessEngineConfiguration()
      .getRuntimeService()
      .newMigration(batchConfiguration.getMigrationPlan())
        .processInstanceIds(batchConfiguration.getProcessInstanceIds());

    if (batchConfiguration.isSkipCustomListeners()) {
      executionBuilder.skipCustomListeners();
    }
    if (batchConfiguration.isSkipIoMappings()) {
      executionBuilder.skipIoMappings();
    }

    // uses internal API in order to skip writing user operation log (CommandContext#disableUserOperationLog
    // is not sufficient with legacy engine config setting "restrictUserOperationLogToAuthenticatedUsers" = false)
    ((MigrationPlanExecutionBuilderImpl) executionBuilder).execute(false);

    commandContext.getByteArrayManager().delete(configurationEntity);
  }

  protected ProcessDefinitionEntity getProcessDefinition(CommandContext commandContext, String processDefinitionId) {
    return commandContext.getProcessEngineConfiguration()
      .getDeploymentCache()
      .findDeployedProcessDefinitionById(processDefinitionId);
  }

  protected JobEntity createBatchJob(BatchEntity batch, ByteArrayEntity configuration) {
    BatchJobContext creationContext = new BatchJobContext(batch, configuration);
    return JOB_DECLARATION.createJobInstance(creationContext);
  }

}
