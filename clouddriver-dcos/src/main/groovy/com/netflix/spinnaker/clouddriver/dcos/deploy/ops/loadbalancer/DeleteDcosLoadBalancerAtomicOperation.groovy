package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.DeleteDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.exception.DcosOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

class DeleteDcosLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_LOAD_BALANCER"

  private final DcosClientProvider dcosClientProvider
  private final DcosDeploymentMonitor deploymentMonitor
  private final DeleteDcosLoadBalancerAtomicOperationDescription description

  DeleteDcosLoadBalancerAtomicOperation(DcosClientProvider dcosClientProvider, DcosDeploymentMonitor deploymentMonitor,
                                        DeleteDcosLoadBalancerAtomicOperationDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.deploymentMonitor = deploymentMonitor
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of load balancer $description.loadBalancerName..."

    DCOS dcosClient = dcosClientProvider.getDcosClient(description.credentials);

    DcosSpinnakerId appId = DcosSpinnakerId.from(description.credentials.name,
            description.group,
            "load-balancer-$description.loadBalancerName");

    App existingLb = dcosClient.maybeApp(appId.toString())
            .orElseThrow({
      throw new DcosOperationException("Unable to find an instance of load balancer with name $description.loadBalancerName")
    })

    dcosClient.deleteApp(existingLb.id)
    deploymentMonitor.waitForAppDestroy(dcosClient, existingLb, null, task, BASE_PHASE);

    task.updateStatus BASE_PHASE, "Successfully deleted load balancer $description.loadBalancerName."
  }
}
