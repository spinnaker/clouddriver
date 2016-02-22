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

package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class AzureSecurityGroupDescription extends AzureResourceOpsDescription {
  String securityGroupName
  String resourceId
  String id
  String etag
  String location
  String type
  Map<String, String> tags = [:]
  String provisioningState
  String resourceGuid
  List<AzureSGRule> securityRules = []
  List<String> networkInterfaces = []
  List<String> subnets = []
  String subnet

  static class AzureSGRule {
    String id
    String name

    String resourceId /*Azure resource ID */
    String description /* restricted to 140 chars */
    String access /* gets or sets network traffic is allowed or denied; possible values are “Allow” and “Deny” */
    String destinationAddressPrefix /* CIDR or destination IP range; asterix “*” can also be used to match all source IPs; default tags such as ‘VirtualNetwork’, ‘AzureLoadBalancer’ and ‘Internet’ can also be used */
    String destinationPortRange /* Integer or range between 0 and 65535; asterix “*” can also be used to match all ports */
    String direction /* InBound or Outbound */
    Integer priority /* value can be between 100 and 4096 */
    String protocol /* Tcp, Udp or All(*) */
    String sourceAddressPrefix /* CIDR or source IP range; asterix “*” can also be used to match all source IPs; default tags such as ‘VirtualNetwork’, ‘AzureLoadBalancer’ and ‘Internet’ can also be used */
    String sourcePortRange /* Integer or range between 0 and 65535; asterix “*” can also be used to match all ports */
  }
}
