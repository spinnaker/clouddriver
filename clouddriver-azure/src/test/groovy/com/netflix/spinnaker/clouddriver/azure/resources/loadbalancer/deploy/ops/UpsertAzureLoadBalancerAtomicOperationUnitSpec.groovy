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
package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.deploy.ops

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.azure.security.AzureNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.converters.UpsertAzureLoadBalancerAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.azure.templates.AzureLoadBalancerResourceTemplate
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.ops.UpsertAzureLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Shared
import spock.lang.Specification

class UpsertAzureLoadBalancerAtomicOperationUnitSpec extends Specification {
  private static final LOAD_BALANCER_NAME = "azureapp1-st1-d1"
  private static final REGION = "West US"
  private static final ACCOUNT_NAME = "azurecred1"
  private static final CLOUD_PROVIDER = "azure"
  private static final APP_NAME = "azureapp1"
  private static final STACK = "st1"
  private static final DETAIL = "d1"
  private static final VNET = "vnet-westus-azureapp1-st1-d1"
  private static final SECURITY_GROUPS = "azureapp1-sg1-d1"
  private static final PROBE_NAME1 = "healthcheck1"
  private static final PROBE_PROTOCOL1 = "HTTP"
  private static final PROBE_PORT1 = 7001
  private static final PROBE_PATH1 = "/healthcheck"
  private static final PROBE_INTERVAL1 = 10
  private static final PROBE_UNHEALTHY_THRESHOLD1 = 2
  private static final PROBE_NAME2 = "healthcheck2"
  private static final PROBE_PROTOCOL2 = "TCP"
  private static final PROBE_PORT2 = 7002
  private static final PROBE_INTERVAL2 = 12
  private static final PROBE_UNHEALTHY_THRESHOLD2 = 4
  private static final LB_RULE_NAME1 = "lbRule1"
  private static final LB_RULE_PROTOCOL1 = "TCP"
  private static final LB_RULE_EXTERNAL_PORT1 = 80
  private static final LB_RULE_BACKEND_PORT1 = 80
  private static final LB_RULE_PROBE_NAME1 = "healthcheck1"
  private static final LB_RULE_IDLE_TIMEOUT1 = 4
  private static final LB_RULE_NAME2 = "lbRule2"
  private static final LB_RULE_PROTOCOL2 = "TCP"
  private static final LB_RULE_EXTERNAL_PORT2 = 8080
  private static final LB_RULE_BACKEND_PORT2 = 8080
  private static final LB_RULE_PROBE_NAME2 = "healthcheck2"
  private static final LB_RULE_IDLE_TIMEOUT2 = 5

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertAzureLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))

    this.converter = new UpsertAzureLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(AzureNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "create a load balancer dynamic template with two probes and two listners/load balancer rules"() {
    setup:
      def input = [
        loadBalancerName: LOAD_BALANCER_NAME,
        region: REGION,
        accountName: ACCOUNT_NAME,
        cloudProvider: CLOUD_PROVIDER,
        appName: APP_NAME,
        stack: STACK,
        detail: DETAIL,
        vnet: VNET,
        securityGroups: SECURITY_GROUPS,
        probes: [
          [
            probeName: PROBE_NAME1,
            probeProtocol: PROBE_PROTOCOL1,
            probePort: PROBE_PORT1,
            probePath: PROBE_PATH1,
            probeInterval: PROBE_INTERVAL1,
            unhealthyThreshold: PROBE_UNHEALTHY_THRESHOLD1
          ],
          [
            probeName: PROBE_NAME2,
            probeProtocol: PROBE_PROTOCOL2,
            probePort: PROBE_PORT2,
            probeInterval: PROBE_INTERVAL2,
            unhealthyThreshold: PROBE_UNHEALTHY_THRESHOLD2
          ]
        ],
        loadBalancingRules: [
          [
            ruleName: LB_RULE_NAME1,
            protocol: LB_RULE_PROTOCOL1,
            externalPort: LB_RULE_EXTERNAL_PORT1,
            backendPort: LB_RULE_BACKEND_PORT1,
            probeName: LB_RULE_PROBE_NAME1,
            idleTimeout: LB_RULE_IDLE_TIMEOUT1
          ],
          [
            ruleName: LB_RULE_NAME2,
            protocol: LB_RULE_PROTOCOL2,
            externalPort: LB_RULE_EXTERNAL_PORT2,
            backendPort: LB_RULE_BACKEND_PORT2,
            probeName: LB_RULE_PROBE_NAME2,
            idleTimeout: LB_RULE_IDLE_TIMEOUT2
          ]
        ]
      ]

      def description = converter.convertDescription(input)
      // def operation = converter.convertOperation(input)
      //def operation = new UpsertAzureLoadBalancerAtomicOperation(description)

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof UpsertAzureLoadBalancerAtomicOperation

    when:
      String upsertLoadBalancerTemplate = AzureLoadBalancerResourceTemplate.getTemplate(description)

    then:
      upsertLoadBalancerTemplate.contains(LOAD_BALANCER_NAME)
  }

}
