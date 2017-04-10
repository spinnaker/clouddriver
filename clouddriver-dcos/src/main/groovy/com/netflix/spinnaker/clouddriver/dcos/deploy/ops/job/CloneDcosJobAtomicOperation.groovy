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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.dcos.api.DcosApiConverter
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.CloneDcosJobAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.exception.DcosResourceNotFoundException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.dcos.api.model.Pod

class CloneDcosJobAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CLONE_JOB"

  CloneDcosJobAtomicOperation(CloneDcosJobAtomicOperationDescription description) {
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  CloneDcosJobAtomicOperationDescription description

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ { "cloneJob": { "source": { "jobName": "kub-test-xdfasdf" }, "account":  "my-dcos-account" } } ]' localhost:7002/dcos/ops
  */
  @Override
  DeploymentResult operate(List priorOutputs) {
    CloneDcosJobAtomicOperationDescription newDescription = cloneAndOverrideDescription()

    task.updateStatus BASE_PHASE, "Initializing copy of job for ${description.source.jobName}..."

    RunDcosJobAtomicOperation deployer = new RunDcosJobAtomicOperation(newDescription)
    DeploymentResult deploymentResult = deployer.operate(priorOutputs)

    task.updateStatus BASE_PHASE, "Finished copying job for ${description.source.jobName}."

    task.updateStatus BASE_PHASE, "Finished copying job for ${description.source.jobName}. New job = ${deploymentResult.deployedNames[0]}."

    return deploymentResult
  }

  CloneDcosJobAtomicOperationDescription cloneAndOverrideDescription() {
    CloneDcosJobAtomicOperationDescription newDescription = description.clone()

    task.updateStatus BASE_PHASE, "Reading ancestor job ${description.source.jobName}..."

    def credentials = newDescription.credentials.credentials

    newDescription.source.namespace = description.source.namespace ?: "default"
    Pod ancestorPod = credentials.apiAdaptor.getPod(newDescription.source.namespace, newDescription.source.jobName)

    if (!ancestorPod) {
      throw new DcosResourceNotFoundException("Source job $newDescription.source.jobName does not exist.")
    }

    def ancestorNames = Names.parseName(description.source.jobName)

    // Build description object from ancestor, override any values that were specified on the clone call
    newDescription.application = description.application ?: ancestorNames.app
    newDescription.stack = description.stack ?: ancestorNames.stack
    newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    newDescription.namespace = description.namespace ?: description.source.namespace
    if (!description.container) {
      newDescription.container = DcosApiConverter.fromContainer(ancestorPod.spec?.containers?.get(0))
    }

    return newDescription
  }
}
