/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.dcos.health.DcosHealthIndicator
import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentialsInitializer
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@ConditionalOnProperty('dcos.enabled')
@EnableConfigurationProperties
@EnableScheduling
@ComponentScan(["com.netflix.spinnaker.clouddriver.dcos"])
@Import([DcosCredentialsInitializer])
class DcosConfiguration {
  @Bean
  @ConfigurationProperties("dcos")
  DcosConfigurationProperties dcosConfigurationProperties() {
    new DcosConfigurationProperties()
  }

  @Bean
  DcosClientProvider dcosClientProvider(AccountCredentialsProvider credentialsProvider) {
    new DcosClientProvider(credentialsProvider)
  }

  @Bean
  DcosHealthIndicator dcosHealthIndicator(AccountCredentialsProvider accountCredentialsProvider, DcosClientProvider dcosClientProvider) {
    new DcosHealthIndicator(accountCredentialsProvider, dcosClientProvider)
  }
// TODO(willgorman): restore in future PRs
//  @Bean
//  DeployDcosServerGroupDescriptionToAppMapper deployDcosServerGroupDescriptionToAppMapper() {
//    new DeployDcosServerGroupDescriptionToAppMapper()
//  }

  @Bean
  OperationPoller dcosOperationPoller(DcosConfigurationProperties properties) {
    new OperationPoller(
      properties.asyncOperationTimeoutSecondsDefault,
      properties.asyncOperationMaxPollingIntervalSeconds
    )
  }
// TODO(willgorman): restore in future PRs
//  @Bean
//  DcosDeploymentMonitor dcosDeploymentMonitor(@Qualifier("dcosOperationPoller") OperationPoller operationPoller) {
//    new PollingDcosDeploymentMonitor(operationPoller)
//  }
}


