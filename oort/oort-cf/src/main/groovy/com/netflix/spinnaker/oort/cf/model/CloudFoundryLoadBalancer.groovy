package com.netflix.spinnaker.oort.cf.model

import com.netflix.spinnaker.oort.model.LoadBalancer
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.cloudfoundry.client.lib.domain.CloudRoute

/**
 * @author Greg Turnquist
 */
@CompileStatic
@EqualsAndHashCode(includes = ["name"])
class CloudFoundryLoadBalancer implements LoadBalancer {

  String name
  String type = 'cf'
  Set<Map<String, Object>> serverGroups = new HashSet<>()
  String region
  String account
  CloudRoute nativeRoute

}
