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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model

import com.microsoft.azure.management.network.models.LoadBalancer
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureLoadBalancerDescription extends AzureResourceOpsDescription {
  String loadBalancerName
  String vnet
  String subnet
  String securityGroup
  String dnsName
  String cluster
  String serverGroup
  String appName
  List<AzureLoadBalancerProbe> probes = []
  List<AzureLoadBalancingRule> loadBalancingRules = []
  List<AzureLoadBalancerInboundNATRule> inboundNATRules = []

  static class AzureLoadBalancerProbe {
    enum AzureLoadBalancerProbesType {
      HTTP, TCP
    }

    String probeName
    AzureLoadBalancerProbesType probeProtocol
    Integer probePort
    String probePath
    Integer probeInterval
    Integer unhealthyThreshold
  }

  static class AzureLoadBalancingRule {
    enum AzureLoadBalancingRulesType {
      TCP, UDP
    }

    String ruleName
    AzureLoadBalancingRulesType protocol
    Integer externalPort
    Integer backendPort
    String probeName
    String persistence
    Integer idleTimeout
  }

  static class AzureLoadBalancerInboundNATRule {
    enum AzureLoadBalancerInboundNATRulesProtocolType {
      HTTP, TCP
    }
    enum AzureLoadBalancerInboundNATRulesServiceType {
      SSH
    }

    String ruleName
    AzureLoadBalancerInboundNATRulesServiceType serviceType
    AzureLoadBalancerInboundNATRulesProtocolType protocol
    Integer port
  }

  static AzureLoadBalancerDescription build(LoadBalancer azureLoadBalancer) {
    AzureLoadBalancerDescription description = new AzureLoadBalancerDescription(loadBalancerName: azureLoadBalancer.name)
    def parsedName = Names.parseName(azureLoadBalancer.name)
    description.stack = azureLoadBalancer.tags?.stack ?: parsedName.stack
    description.detail = azureLoadBalancer.tags?.detail ?: parsedName.detail
    description.appName = azureLoadBalancer.tags?.appName ?: parsedName.app
    description.cluster = azureLoadBalancer.tags?.cluster
    description.serverGroup = azureLoadBalancer.tags?.serverGroup
    description.vnet = azureLoadBalancer.tags?.vnet
    description.createdTime = azureLoadBalancer.tags?.createdTime?.toLong()
    description.tags = azureLoadBalancer.tags
    description.region = azureLoadBalancer.location

    for (def rule : azureLoadBalancer.loadBalancingRules) {
      def r = new AzureLoadBalancingRule(ruleName: rule.name)
      r.externalPort = rule.frontendPort
      r.backendPort = rule.backendPort
      r.probeName = AzureUtilities.getNameFromResourceId(rule?.probe?.id) ?: "not-assigned"
      r.persistence = rule.loadDistribution;
      r.idleTimeout = rule.idleTimeoutInMinutes;

      if (rule.protocol.toLowerCase() == "udp") {
        r.protocol = AzureLoadBalancingRule.AzureLoadBalancingRulesType.UDP
      } else {
        r.protocol = AzureLoadBalancingRule.AzureLoadBalancingRulesType.TCP
      }
      description.loadBalancingRules.add(r)
    }

    // Add the probes
    for (def probe : azureLoadBalancer.probes) {
      def p = new AzureLoadBalancerProbe()
      p.probeName = probe.name
      p.probeInterval = probe.intervalInSeconds
      p.probePath = probe.requestPath
      p.probePort = probe.port
      p.unhealthyThreshold = probe.numberOfProbes
      if (probe.protocol.toLowerCase() == "tcp") {
        p.probeProtocol = AzureLoadBalancerProbe.AzureLoadBalancerProbesType.TCP
      } else {
        p.probeProtocol = AzureLoadBalancerProbe.AzureLoadBalancerProbesType.HTTP
      }
      description.probes.add(p)
    }

    for (def natRule : azureLoadBalancer.inboundNatRules) {
      def n = new AzureLoadBalancerInboundNATRule(ruleName: natRule.name)
      description.inboundNATRules.add(n)
    }

    description
  }

}
