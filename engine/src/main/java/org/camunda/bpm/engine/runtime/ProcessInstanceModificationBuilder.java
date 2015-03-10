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
package org.camunda.bpm.engine.runtime;

/**
 * @author Thorben Lindhauer
 *
 */
public interface ProcessInstanceModificationBuilder {

  ProcessInstanceModificationBuilder cancelActivityInstance(String activityInstanceId);

  ProcessInstanceModificationBuilder cancelAllInActivity(String activityId);

  ProcessInstanceModificationBuilder startBeforeActivity(String activityId);

  ProcessInstanceModificationBuilder startBeforeActivity(String activityId, String ancestorActivityInstanceId);

  ProcessInstanceModificationBuilder startAfterActivity(String activityId);

  ProcessInstanceModificationBuilder startAfterActivity(String activityId, String ancestorActivityInstanceId);

  ProcessInstanceModificationBuilder startTransition(String transitionId);

  ProcessInstanceModificationBuilder startTransition(String transitionId, String ancestorActivityInstanceId);

  ProcessInstanceModificationBuilder setVariable(String name, Object value);

  ProcessInstanceModificationBuilder setVariableLocal(String name, Object value);

  void execute();

  void execute(boolean skipCustomListeners, boolean skipIoMappings);



}
