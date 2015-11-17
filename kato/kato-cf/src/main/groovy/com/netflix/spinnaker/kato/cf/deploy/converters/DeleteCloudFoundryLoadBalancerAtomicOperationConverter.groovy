package com.netflix.spinnaker.kato.cf.deploy.converters
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.kato.cf.deploy.description.DeleteCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.kato.cf.deploy.ops.loadbalancer.DeleteCloudFoundryLoadBalancerAtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component
/**
 * @author Greg Turnquist
 */
@CloudFoundryOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteCloudFoundryLoadBalancerDescription")
class DeleteCloudFoundryLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new DeleteCloudFoundryLoadBalancerAtomicOperation(convertDescription(input))
  }

  @Override
  Object convertDescription(Map input) {
    new DeleteCloudFoundryLoadBalancerDescription([
        loadBalancerName : input.loadBalancerName,
        zone             : input.zone,
        region           : input.region,
        credentials      : getCredentialsObject(input.credentials as String)
    ])
  }
}
