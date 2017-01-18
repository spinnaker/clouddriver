package com.netflix.spinnaker.clouddriver.dcos.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.ResizeDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class ResizeDcosServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE"

  final DcosClientProvider dcosClientProvider
  final ResizeDcosServerGroupDescription description

  ResizeDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, ResizeDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "kub-test-v000", "capacity": { "desired": 7 }, "account": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName..."

    def credentials = description.credentials.credentials
    def size = description.capacity.desired
    def name = description.serverGroupName

    task.updateStatus BASE_PHASE, "Setting size to $size..."

//    def desired = null
//    def getGeneration = null
//    def getResource = null
//    if (credentials.apiAdaptor.getReplicationController(namespace, name)) {
//      desired = credentials.apiAdaptor.resizeReplicationController(namespace, name, size)
//      getGeneration = { ReplicationController rc ->
//        return rc.metadata.generation
//      }
//      getResource = {
//        return credentials.apiAdaptor.getReplicationController(namespace, name)
//      }
//    } else if (credentials.apiAdaptor.getReplicaSet(namespace, name)) {
//      desired = credentials.apiAdaptor.resizeReplicaSet(namespace, name, size)
//      getGeneration = { ReplicaSet rs ->
//        return rs.metadata.generation
//      }
//      getResource = {
//        return credentials.apiAdaptor.getReplicaSet(namespace, name)
//      }
//    } else {
//      throw new DcosOperationException("Neither a replication controller nor a replica set could be found by that name.")
//    }
//
//    if (!credentials.apiAdaptor.blockUntilResourceConsistent(desired, getGeneration, getResource)) {
//      throw new DcosOperationException("Failed waiting for server group to acknowledge its new size. This is likely a bug within Kubernetes itself.")
//    }

    task.updateStatus BASE_PHASE, "Completed resize operation."
  }
}

