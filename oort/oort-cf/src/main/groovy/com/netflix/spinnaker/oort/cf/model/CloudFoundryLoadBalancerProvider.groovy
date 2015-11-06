package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * @author Greg Turnquist
 */
@Component
class CloudFoundryLoadBalancerProvider implements LoadBalancerProvider<CloudFoundryLoadBalancer> {

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  @Override
  Map<String, Set<CloudFoundryLoadBalancer>> getLoadBalancers() {
    cloudFoundryResourceRetriever.loadBalancersByAccount
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancers(String account) {
    cloudFoundryResourceRetriever.loadBalancersByAccount[account]
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancers(String account, String cluster) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster]
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster]
  }

  @Override
  Set<CloudFoundryLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster].findAll{
      it.name == loadBalancerName
    } as Set<CloudFoundryLoadBalancer>
  }

  @Override
  CloudFoundryLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    cloudFoundryResourceRetriever.loadBalancersByAccountAndClusterName[account][cluster].findAll{
      it.name == loadBalancerName
    } as Set<CloudFoundryLoadBalancer>
  }

  @Override
  Set<CloudFoundryLoadBalancer> getApplicationLoadBalancers(String application) {
    cloudFoundryResourceRetriever.loadBalancersByApplication[application]
  }
}
