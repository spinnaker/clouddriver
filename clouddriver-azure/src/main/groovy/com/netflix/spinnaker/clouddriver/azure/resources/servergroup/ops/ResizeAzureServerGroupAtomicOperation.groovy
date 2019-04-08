/*
 * Copyright 2019 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.cluster.view.AzureClusterProvider
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.ResizeAzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationException
import org.springframework.beans.factory.annotation.Autowired

class ResizeAzureServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ResizeAzureServerGroupDescription description

  @Autowired
  AzureClusterProvider azureClusterProvider

  ResizeAzureServerGroupAtomicOperation(ResizeAzureServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "myapp-dev-v000", "targetSize": 2, "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/azure/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Resize Azure Server Group Operation..."

    def region = description.region
    if (description.serverGroupName) description.name = description.serverGroupName
    if (!description.application) description.application = description.appName ?: Names.parseName(description.name).app
    int targetSize = description.targetSize instanceof Number ? description.targetSize : description.capacity.desired
    task.updateStatus BASE_PHASE, "Resizing server group ${description.name} " + "in ${region} to target size ${targetSize}..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for the selected Azure account.")
    }

    def errList = new ArrayList<String>()

    try {
      String resourceGroupName = AzureUtilities.getResourceGroupName(description.application, region)
      AzureServerGroupDescription serverGroupDescription = description.credentials.computeClient.getServerGroup(resourceGroupName, description.name)

      if (!serverGroupDescription) {
        task.updateStatus(BASE_PHASE, "Resize Server Group Operation failed: could not find server group ${description.name} in ${region}")
        errList.add("could not find server group ${description.name} in ${region}")
      } else {
        try {
          description
            .credentials
            .computeClient
            .resizeServerGroup(resourceGroupName, description.name, targetSize)

          task.updateStatus BASE_PHASE, "Done resizing Azure server group ${description.name} in ${region}."
        } catch (Exception e) {
          task.updateStatus(BASE_PHASE, "Resizing server group ${description.name} failed: ${e.message}")
          errList.add("Failed to resize server group ${description.name}: ${e.message}")
        }
      }
    } catch (Exception e) {
      task.updateStatus(BASE_PHASE, "Resizing server group ${description.name} failed: ${e.message}")
      errList.add("Failed to resize server group ${description.name}: ${e.message}")
    }

    if (errList.isEmpty()) {
      task.updateStatus BASE_PHASE, "Resize Azure Server Group Operation for ${description.name} succeeded."
    }
    else {
      errList.add(" Go to Azure Portal for more info")
      throw new AtomicOperationException("Failed to resize ${description.name}", errList)
    }

    null
  }
}
