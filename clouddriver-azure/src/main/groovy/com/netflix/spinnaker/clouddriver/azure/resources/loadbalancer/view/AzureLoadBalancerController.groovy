/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProviderTempShim
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


/**
 * @deprecated - Use AzureAppGatewayController instead.
 */
@Deprecated
class AzureLoadBalancerController implements LoadBalancerProviderTempShim {

  final String cloudProvider = "DoNotUse"

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AzureLoadBalancerProvider azureLoadBalancerProvider

  List<AzureLoadBalancerSummary> list() {
    getSummaryForLoadBalancers().values() as List
  }

  private Map<String, AzureLoadBalancerSummary> getSummaryForLoadBalancers() {
    Map<String, AzureLoadBalancerSummary> map = [:]
    def loadBalancers = azureLoadBalancerProvider.getApplicationLoadBalancers('*')

    loadBalancers?.each() { lb ->
          def summary = map.get(lb.name)

          if (!summary) {
            summary = new AzureLoadBalancerSummary(name: lb.name)
            map.put lb.name, summary
          }

          def loadBalancerDetail = new AzureLoadBalancerDetail(account: lb.account, name: lb.name, region: lb.region)

          summary.getOrCreateAccount(lb.account).getOrCreateRegion(lb.region).loadBalancers << loadBalancerDetail
    }
    map
  }

  @Override
  LoadBalancerProviderTempShim.Item get(String name) {
    throw new UnsupportedOperationException("TODO: Implement single getter.")
  }

  List<Map> byAccountAndRegionAndName(String account, String region, String name) {
    String appName = AzureUtilities.getAppNameFromAzureResourceName(name)
    AzureLoadBalancerDescription azureLoadBalancerDescription = azureLoadBalancerProvider.getLoadBalancerDescription(account, appName, region, name)

    if (azureLoadBalancerDescription) {
      def lbDetail = [
        name: azureLoadBalancerDescription.loadBalancerName
      ]

      lbDetail.createdTime = azureLoadBalancerDescription.createdTime
      lbDetail.serverGroup = azureLoadBalancerDescription.serverGroup
      lbDetail.vnet = azureLoadBalancerDescription.vnet ?: "vnet-unassigned"
      lbDetail.subnet = azureLoadBalancerDescription.subnet ?: "subnet-unassigned"
      lbDetail.dnsName = azureLoadBalancerDescription.dnsName ?: "dnsname-unassigned"

      lbDetail.probes = azureLoadBalancerDescription.probes
      lbDetail.securityGroup = azureLoadBalancerDescription.securityGroup
      lbDetail.loadBalancingRules = azureLoadBalancerDescription.loadBalancingRules
      lbDetail.inboundNATRules = azureLoadBalancerDescription.inboundNATRules
      lbDetail.tags = azureLoadBalancerDescription.tags

      return [lbDetail]
    }

    return []
  }

  static class AzureLoadBalancerSummary implements LoadBalancerProviderTempShim.Item {
    private Map<String, AzureLoadBalancerAccount> mappedAccounts = [:]
    String name

    AzureLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AzureLoadBalancerAccount(name:name))
      }

      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AzureLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class AzureLoadBalancerAccount implements LoadBalancerProviderTempShim.ByAccount {
    private Map<String, AzureLoadBalancerAccountRegion> mappedRegions = [:]
    String name

    AzureLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new AzureLoadBalancerAccountRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name];
    }

    @JsonProperty("regions")
    List<AzureLoadBalancerAccountRegion> getByRegions() {
      mappedRegions.values() as List
    }

  }

  static class AzureLoadBalancerAccountRegion implements LoadBalancerProviderTempShim.Details {
    String name
    List<AzureLoadBalancerDetail> loadBalancers
  }

  static class AzureLoadBalancerDetail implements LoadBalancerProviderTempShim.Details {
    String account
    String region
    String name
    String type="azure"
  }
}
