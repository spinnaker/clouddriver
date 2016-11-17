/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.exceptions.CloudProviderNotFoundException
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProviderTempShim
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class LoadBalancerController {

  @Autowired
  List<LoadBalancerProvider> loadBalancerProviders

  @Autowired(required = false)
  List<LoadBalancerProviderTempShim> tempShims

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/loadBalancers", method = RequestMethod.GET)
  List<LoadBalancer> list(@PathVariable String application) {
    loadBalancerProviders.findResults {
      it.getApplicationLoadBalancers(application)
    }
    .flatten()
    .sort { a, b -> a.name.toLowerCase() <=> b.name.toLowerCase() } as List<LoadBalancer>
  }

  @RequestMapping(value = "/{cloudProvider:.+}/loadBalancers", method = RequestMethod.GET)
  List<LoadBalancerProviderTempShim.Item> listForCloudProvider(@PathVariable String cloudProvider) {
    return findLoadBalancerProvider(cloudProvider).list()
  }

  @RequestMapping(value = "/{cloudProvider:.+}/loadBalancers/{name:.+}", method = RequestMethod.GET)
  LoadBalancerProviderTempShim.Item get(@PathVariable String cloudProvider,
                                        @PathVariable String name) {
    return findLoadBalancerProvider(cloudProvider).get(name)
  }

  @RequestMapping(value = "/{cloudProvider:.+}/loadBalancers/{account:.+}/{region:.+}/{name:.+}",
                  method = RequestMethod.GET)
  List<LoadBalancerProviderTempShim.Details> getByAccountRegionName(@PathVariable String cloudProvider,
                                                                    @PathVariable String account,
                                                                    @PathVariable String region,
                                                                    @PathVariable String name) {
    return findLoadBalancerProvider(cloudProvider).byAccountAndRegionAndName(account, region, name)
  }

  private LoadBalancerProviderTempShim findLoadBalancerProvider(String cloudProvider) {
    return tempShims
        .stream()
        .filter({ it.cloudProvider == cloudProvider })
        .findFirst()
        .orElseThrow({ new CloudProviderNotFoundException("No cloud provider named ${cloudProvider} found") })
  }
}
