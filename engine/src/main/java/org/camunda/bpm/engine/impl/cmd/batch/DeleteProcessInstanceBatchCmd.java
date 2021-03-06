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

package org.camunda.bpm.engine.impl.cmd.batch;

import org.camunda.bpm.engine.batch.Batch;
import org.camunda.bpm.engine.history.UserOperationLogEntry;
import org.camunda.bpm.engine.impl.ProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.batch.BatchEntity;
import org.camunda.bpm.engine.impl.batch.BatchJobHandler;
import org.camunda.bpm.engine.impl.batch.deletion.DeleteProcessInstanceBatchConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyChange;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotEmpty;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;

/**
 * @author Askar Akhmerov
 */
public class DeleteProcessInstanceBatchCmd extends AbstractBatchCmd<Batch> {
  protected final String deleteReason;
  protected List<String> processInstanceIds;
  protected ProcessInstanceQuery processInstanceQuery;

  public DeleteProcessInstanceBatchCmd(List<String> processInstances, String deleteReason) {
    super();
    this.processInstanceIds = processInstances;
    this.deleteReason = deleteReason;
  }

  public DeleteProcessInstanceBatchCmd(ProcessInstanceQuery processInstanceQuery, String deleteReason) {
    super();
    ensureNotNull("processInstanceQuery", processInstanceQuery);
    this.processInstanceQuery = processInstanceQuery;
    this.deleteReason = deleteReason;
  }

  protected List<String> collectProcessInstanceIds(ProcessInstanceQuery processInstanceQuery) {
    return ((ProcessInstanceQueryImpl)processInstanceQuery).listIds();
  }

  public List<String> getProcessInstanceIds() {
    return processInstanceIds;
  }

  @Override
  public Batch execute(CommandContext commandContext) {
    if (processInstanceIds == null && processInstanceQuery != null){
      processInstanceIds = collectProcessInstanceIds(processInstanceQuery);
    }

    ensureNotNull("processInstanceIds", processInstanceIds);
    ensureNotEmpty("processInstanceIds", processInstanceIds);
    checkAuthorizations(commandContext);
    writeUserOperationLog(commandContext,
        deleteReason,
        processInstanceIds.size(),
        true);

    BatchEntity batch = createBatch(commandContext, processInstanceIds, deleteReason);

    batch.createSeedJobDefinition();
    batch.createMonitorJobDefinition();
    batch.createBatchJobDefinition();

    batch.fireHistoricStartEvent();

    batch.createSeedJob();

    return batch;
  }



  protected BatchEntity createBatch(CommandContext commandContext, List<String> processInstanceIds, String deleteReason) {
    ProcessEngineConfigurationImpl processEngineConfiguration = commandContext.getProcessEngineConfiguration();
    BatchJobHandler<DeleteProcessInstanceBatchConfiguration> batchJobHandler = getBatchJobHandler(processEngineConfiguration);

    DeleteProcessInstanceBatchConfiguration configuration = DeleteProcessInstanceBatchConfiguration
        .create(processInstanceIds,deleteReason);

    BatchEntity batch = new BatchEntity();
    batch.setType(batchJobHandler.getType());
    batch.setTotalJobs(calculateSize(processEngineConfiguration, configuration));
    batch.setBatchJobsPerSeed(processEngineConfiguration.getBatchJobsPerSeed());
    batch.setInvocationsPerBatchJob(processEngineConfiguration.getInvocationsPerBatchJob());
    batch.setConfigurationBytes(batchJobHandler.writeConfiguration(configuration));
    commandContext.getBatchManager().insert(batch);

    return batch;
  }

  protected int calculateSize(ProcessEngineConfigurationImpl engineConfiguration, DeleteProcessInstanceBatchConfiguration batchConfiguration) {
    int invocationsPerBatchJob = engineConfiguration.getInvocationsPerBatchJob();
    int processInstanceCount = batchConfiguration.getProcessInstanceIds().size();

    return (int) Math.ceil(processInstanceCount / invocationsPerBatchJob);
  }

  protected BatchJobHandler<DeleteProcessInstanceBatchConfiguration> getBatchJobHandler(ProcessEngineConfigurationImpl processEngineConfiguration) {
    return (BatchJobHandler<DeleteProcessInstanceBatchConfiguration>) processEngineConfiguration.getBatchHandlers().get(Batch.TYPE_PROCESS_INSTANCE_DELETION);
  }

  protected void writeUserOperationLog(CommandContext commandContext,
                                       String deletionReason,
                                       int numInstances,
                                       boolean async) {

    List<PropertyChange> propertyChanges = new ArrayList<PropertyChange>();
    propertyChanges.add(new PropertyChange("nrOfInstances",
        null,
        numInstances));
    propertyChanges.add(new PropertyChange("async", null, async));
    propertyChanges.add(new PropertyChange("deletionReason", null, deletionReason));

    commandContext.getOperationLogManager()
        .logProcessInstanceOperation(UserOperationLogEntry.OPERATION_TYPE_DELETE,
            null,
            null,
            null,
            propertyChanges);
  }

}
