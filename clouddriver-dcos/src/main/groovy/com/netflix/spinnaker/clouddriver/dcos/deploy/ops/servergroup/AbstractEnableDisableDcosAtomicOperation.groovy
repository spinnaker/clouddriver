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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.EnableDisablePercentageCategorizer
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosUtil
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.EnableDisableDcosAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.exception.DcosOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import io.fabric8.dcos.api.model.Pod
import io.fabric8.dcos.api.model.ReplicationController
import io.fabric8.dcos.api.model.extensions.ReplicaSet

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class AbstractEnableDisableDcosAtomicOperation implements AtomicOperation<Void> {
  abstract String getBasePhase() // Either 'ENABLE' or 'DISABLE'.
  abstract String getAction() // Either 'true' or 'false', for Enable or Disable respectively.
  abstract String getVerb() // Either 'enabling' or 'disabling.
  EnableDisableDcosAtomicOperationDescription description

  AbstractEnableDisableDcosAtomicOperation(DcosServerGroupDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus basePhase, "Initializing ${basePhase.toLowerCase()} operation..."
    task.updateStatus basePhase, "Looking up provided namespace..."

    def credentials = description.credentials.credentials
    def namespace = DcosUtil.validateNamespace(credentials, description.namespace)

    task.updateStatus basePhase, "Finding requisite server group..."

    def replicationController = credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)
    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)

    task.updateStatus basePhase, "Getting list of attached services..."

    List<String> services = DcosUtil.getLoadBalancers(replicationController ?: replicaSet)
    services = services.collect {
      DcosUtil.loadBalancerKey(it)
    }

    task.updateStatus basePhase, "Resetting server group service template labels and selectors..."

    def getGeneration = null
    def getResource = null
    def desired = null
    def pods = []
    if (replicationController) {
      desired = credentials.apiAdaptor.toggleReplicationControllerSpecLabels(namespace, description.serverGroupName, services, action)
      getGeneration = { ReplicationController rc ->
        return rc.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)
      }
      pods = credentials.apiAdaptor.getReplicationControllerPods(namespace, description.serverGroupName)
    } else if (replicaSet) {
      desired = credentials.apiAdaptor.toggleReplicaSetSpecLabels(namespace, description.serverGroupName, services, action)
      getGeneration = { ReplicaSet rs ->
        return rs.metadata.generation
      }
      getResource = {
        return credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)
      }
      pods = credentials.apiAdaptor.getReplicaSetPods(namespace, description.serverGroupName)
    } else {
      throw new DcosOperationException("No replication controller or replica set $description.serverGroupName in $namespace.")
    }

    if (!credentials.apiAdaptor.blockUntilResourceConsistent(desired, getGeneration, getResource)) {
      throw new DcosOperationException("Server group failed to reach a consistent state. This is likely a bug with Dcos itself.")
    }

    task.updateStatus basePhase, "Resetting service labels for each pod..."

    def pool = Executors.newWorkStealingPool((int) (pods.size() / 2) + 1)

    if (description.desiredPercentage != null) {
      List<Pod> modifiedPods = pods.findAll { pod ->
        DcosUtil.getPodLoadBalancerStates(pod).every { it.value == action }
      }

      List<Pod> unmodifiedPods = pods.findAll { pod ->
        DcosUtil.getPodLoadBalancerStates(pod).any { it.value != action }
      }

      pods = EnableDisablePercentageCategorizer.getInstancesToModify(modifiedPods, unmodifiedPods, description.desiredPercentage)
    }

    pods.each { Pod pod ->
      pool.submit({ _ ->
        List<String> podServices = DcosUtil.getLoadBalancers(pod)
        podServices = podServices.collect {
          DcosUtil.loadBalancerKey(it)
        }
        credentials.apiAdaptor.togglePodLabels(namespace, pod.metadata.name, podServices, action)
      })
    }

    pool.shutdown();
    pool.awaitTermination(1, TimeUnit.HOURS)

    task.updateStatus basePhase, "Finished ${verb} server group."

    null // Return nothing from void
  }
}
