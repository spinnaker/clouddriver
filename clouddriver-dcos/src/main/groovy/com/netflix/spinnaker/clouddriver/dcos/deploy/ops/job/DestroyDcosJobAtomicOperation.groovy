/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosUtil
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.DcosJobDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.exception.DcosOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DestroyDcosJobAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DcosJobDescription description

  DestroyDcosJobAtomicOperation(DcosJobDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyJob": { "jobName": "kub-test-xy8813", "namespace": "default", "credentials": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destroy of job."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = DcosUtil.validateNamespace(credentials, description.namespace)

    task.updateStatus BASE_PHASE, "Destroying job..."

    if (!credentials.apiAdaptor.hardDestroyPod(namespace, description.jobName)) {
      throw new DcosOperationException("Failed to delete $description.jobName in $namespace.")
    }

    task.updateStatus BASE_PHASE, "Successfully destroyed job $description.jobName."
  }
}
