package com.netflix.spinnaker.kato.cf.deploy.description

import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials

/**
 * @author Greg Turnquist
 */
class DeleteCloudFoundryLoadBalancerDescription {
  String loadBalancerName
  String region
  String zone
  String getAccountName() {
    credentials?.name
  }
  CloudFoundryAccountCredentials credentials
}
