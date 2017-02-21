package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instances

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instances.TerminateDcosInstancesAndDecrementDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.DeleteTaskCriteria

class TerminateDcosInstancesAndDecrementAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_AND_DECREMENT"

  final DcosClientProvider dcosClientProvider
  final TerminateDcosInstancesAndDecrementDescription description

    TerminateDcosInstancesAndDecrementAtomicOperation(DcosClientProvider dcosClientProvider, TerminateDcosInstancesAndDecrementDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "hostId": "192.168.10.100", "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "hostId": "192.168.10.100", "force": true, "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "taskIds": ["dcos-test-v000.asdfkjashdfkjashd"], "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "appId": "dcos-test-v000", "taskIds": ["dcos-test-v000.asdfkjashdfkjashd"], "force": true, "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "taskIds": ["dcos-test-v000.asdf1", "dcos-test-v000.asdf2"], "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "taskIds": ["dcos-test-v000.asdf1", "dcos-test-v000.asdf2"], "force": true, "account": "my-dcos-account" }} ]' localhost:7002/dcos/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of instances..."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials)

    if (description.appId) {
      if (description.hostId) {
        dcosClient.deleteAppTasksAndScaleFromHost(description.appId, description.hostId, description.force)
      } else if (description.taskIds) {
        dcosClient.deleteAppTasksAndScaleWithTaskId(description.appId, description.taskIds.first(), description.force)
      }
    } else {
      dcosClient.deleteTaskAndScale(new DeleteTaskCriteria(ids: description.taskIds), description.force)
    }

    task.updateStatus BASE_PHASE, "Completed termination operation."
  }
}
