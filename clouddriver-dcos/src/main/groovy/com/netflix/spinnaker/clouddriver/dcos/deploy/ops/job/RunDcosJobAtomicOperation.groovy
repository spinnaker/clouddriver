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
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.dcos.api.DcosApiConverter
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosJobNameResolver
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosUtil
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.dcos.api.model.Pod
import io.fabric8.dcos.api.model.PodBuilder
import io.fabric8.dcos.api.model.Volume

class RunDcosJobAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "RUN_JOB"

  RunDcosJobAtomicOperation(RunDcosJobDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  RunDcosJobDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "runJob": { "application": "kub", "stack": "test", "loadBalancers":  [],  "container": { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } }, "account":  "my-dcos-account" } } ]' localhost:7002/dcos/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    Pod pod = podDescription()
    return new DeploymentResult([
        deployedNames: [pod.metadata.name],
        deployedNamesByLocation: [(pod.metadata.namespace): [pod.metadata.name]],
    ])
  }

  Pod podDescription() {
    task.updateStatus BASE_PHASE, "Initializing creation of job..."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = DcosUtil.validateNamespace(credentials, description.namespace)

    def podName = (new DcosJobNameResolver()).createJobName(description.application, description.stack, description.freeFormDetails)
    task.updateStatus BASE_PHASE, "JobStatus name chosen to be ${podName}."

    def podBuilder = new PodBuilder().withNewMetadata().withNamespace(namespace).withName(podName).withLabels([:]).endMetadata().withNewSpec()
    podBuilder.withRestartPolicy("Never")
    if (description.volumeSources) {
      List<Volume> volumeSources = description.volumeSources.findResults { volumeSource ->
        DcosApiConverter.toVolumeSource(volumeSource)
      }

      podBuilder = podBuilder.withVolumes(volumeSources)
    }

    for (def imagePullSecret : credentials.imagePullSecrets[namespace]) {
      podBuilder = podBuilder.addNewImagePullSecret(imagePullSecret)
    }

    description.container.name = description.container.name ?: "job"
    def container = DcosApiConverter.toContainer(description.container)

    podBuilder = podBuilder.withContainers(container)

    podBuilder = podBuilder.endSpec()

    task.updateStatus BASE_PHASE, "Sending pod spec to the Dcos master."
    Pod pod = credentials.apiAdaptor.createPod(namespace, podBuilder.build())

    task.updateStatus BASE_PHASE, "Finished creating job ${pod.metadata.name}."

    return pod
  }
}
