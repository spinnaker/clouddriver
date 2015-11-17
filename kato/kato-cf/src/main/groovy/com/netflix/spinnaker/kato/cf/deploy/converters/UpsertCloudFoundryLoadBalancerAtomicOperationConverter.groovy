package com.netflix.spinnaker.kato.cf.deploy.converters

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.kato.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.kato.cf.deploy.ops.loadbalancer.UpsertCloudFoundryLoadBalancerAtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component
/**
 * @author Greg Turnquist
 */
@CloudFoundryOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertCloudFoundryLoadBalancerDescription")
class UpsertCloudFoundryLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  AtomicOperation convertOperation(Map input) {
    new UpsertCloudFoundryLoadBalancerAtomicOperation(convertDescription(input))
  }

  @Override
  Object convertDescription(Map input) {
    new UpsertCloudFoundryLoadBalancerDescription([
        loadBalancerName : input.loadBalancerName,
        zone             : input.zone,
        region           : input.region,
        credentials      : getCredentialsObject(input.credentials as String)
    ])
  }
}
