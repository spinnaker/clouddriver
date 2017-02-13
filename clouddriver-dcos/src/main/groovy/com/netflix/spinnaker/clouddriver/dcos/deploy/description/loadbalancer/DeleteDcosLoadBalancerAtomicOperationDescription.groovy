package com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription

class DeleteDcosLoadBalancerAtomicOperationDescription extends AbstractDcosCredentialsDescription {
  String region
  String loadBalancerName
}
