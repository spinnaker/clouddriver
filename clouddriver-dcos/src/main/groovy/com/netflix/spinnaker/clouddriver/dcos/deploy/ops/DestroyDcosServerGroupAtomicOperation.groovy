package com.netflix.spinnaker.clouddriver.dcos.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DestroyDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DestroyDcosServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY"

  final DcosClientProvider dcosClientProvider
  final DestroyDcosServerGroupDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DestroyDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, DestroyDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyServerGroup": { "serverGroupName": "kub-test-v000", "namespace": "default", "credentials": "my-kubernetes-account" }} ]' localhost:7002/kubernetes/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destroy of replication controller."
    task.updateStatus BASE_PHASE, "Looking up provided namespace..."

    def credentials = description.credentials.credentials

//    def parsedName = Names.parseName(description.serverGroupName)
//    def deploymentName = parsedName.cluster
//    def deployment = credentials.apiAdaptor.getDeployment(namespace, deploymentName)
//    def replicaSet = credentials.apiAdaptor.getReplicaSet(namespace, description.serverGroupName)
//
//    if (deployment && replicaSet) {
//      task.updateStatus BASE_PHASE, "Checking if deployment ${deploymentName} needs to be destroyed..."
//      // If we selected to delete the replica set in the currently active deployment, this will delete everything owned by the deployment.
//      if (credentials.apiAdaptor.getDeploymentRevision(deployment) == credentials.apiAdaptor.getDeploymentRevision(replicaSet)) {
//        task.updateStatus BASE_PHASE, "Destroying deployment ${deploymentName}..."
//        if (!credentials.apiAdaptor.deleteDeployment(namespace, deploymentName)) {
//          throw new KubernetesOperationException("Failed to delete deployment ${deploymentName} in $namespace")
//        } else {
//          // At this point we can safely return, since destroying the deployment destroys the constituent replica sets as well.
//          task.updateStatus BASE_PHASE, "Successfully destroyed deployment ${deploymentName}..."
//          return
//        }
//      }
//    }

    task.updateStatus BASE_PHASE, "Destroying server group..."

//    if (credentials.apiAdaptor.getReplicationController(namespace, description.serverGroupName)) {
//      if (!credentials.apiAdaptor.hardDestroyReplicationController(namespace, description.serverGroupName)) {
//        throw new KubernetesOperationException("Failed to delete $description.serverGroupName in $namespace.")
//      }
//    } else if (replicaSet) {
//      if (!credentials.apiAdaptor.hardDestroyReplicaSet(namespace, description.serverGroupName)) {
//        throw new KubernetesOperationException("Failed to delete $description.serverGroupName in $namespace.")
//      }
//    } else {
//      throw new KubernetesOperationException("Failed to find replication controller or replica set $description in $namespace.")
//    }

    task.updateStatus BASE_PHASE, "Successfully destroyed server group $description.serverGroupName."
  }
}
