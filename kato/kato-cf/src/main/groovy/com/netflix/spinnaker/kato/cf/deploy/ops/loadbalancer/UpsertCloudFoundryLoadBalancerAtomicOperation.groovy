package com.netflix.spinnaker.kato.cf.deploy.ops.loadbalancer
import com.netflix.spinnaker.kato.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryClientFactory
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired
/**
 * @author Greg Turnquist
 */
class UpsertCloudFoundryLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  private final UpsertCloudFoundryLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  UpsertCloudFoundryLoadBalancerAtomicOperation(UpsertCloudFoundryLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing creation of load balancer $description.loadBalancerName in $description.region..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    client.addRoute(description.loadBalancerName, client.defaultDomain.name)

    task.updateStatus BASE_PHASE, "Done creating load balancer $description.loadBalancerName."

    [loadBalancers: [(description.region): [name: description.loadBalancerName]]]
  }

}
