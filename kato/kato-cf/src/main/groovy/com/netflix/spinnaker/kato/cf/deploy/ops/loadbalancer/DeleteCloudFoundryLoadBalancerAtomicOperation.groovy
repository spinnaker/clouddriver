package com.netflix.spinnaker.kato.cf.deploy.ops.loadbalancer
import com.netflix.spinnaker.kato.cf.deploy.description.DeleteCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired
/**
 * @author Greg Turnquist
 */
class DeleteCloudFoundryLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  private final DeleteCloudFoundryLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteCloudFoundryLoadBalancerAtomicOperation(DeleteCloudFoundryLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of load balancer $description.loadBalancerName in $description.region..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    client.deleteRoute(description.loadBalancerName, client.defaultDomain.name)

    task.updateStatus BASE_PHASE, "Done deleting load balancer $description.loadBalancerName."

    null
  }

}
