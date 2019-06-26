/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.health.KubernetesHealthIndicator
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentialsSynchronizer
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials.CredentialFactory
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.KubernetesV1Provider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.KubernetesV1ProviderConfig
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.KubernetesV2Provider
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.KubernetesV2ProviderConfig
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.scheduling.annotation.EnableScheduling

import java.util.concurrent.ConcurrentHashMap

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('kubernetes.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.kubernetes"])
class KubernetesConfiguration {

  @Bean
  KubernetesConfigurationProperties kubernetesConfigurationProperties() {
    new KubernetesConfigurationProperties()
  }

  @Bean
  KubernetesHealthIndicator kubernetesHealthIndicator() {
    new KubernetesHealthIndicator()
  }

  @Bean
  KubernetesUtil kubernetesUtil() {
    new KubernetesUtil()
  }

  @Bean
  KubernetesV1Provider kubernetesV1Provider(KubernetesCloudProvider kubernetesCloudProvider) {
    new KubernetesV1Provider(kubernetesCloudProvider, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()))
  }

  @Bean
  KubernetesV2Provider kubernetesV2Provider() {
    new KubernetesV2Provider()
  }

  @Bean
  @DependsOn(['kubernetesV1Provider', 'kubernetesV2Provider'])
  KubernetesCredentialsSynchronizer kubernetesCredentialsSynchronizer(
    AccountCredentialsRepository accountCredentialsRepository,
    KubernetesConfigurationProperties kubernetesConfigurationProperties,
    KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
    CredentialFactory credentialFactory,
    KubernetesV2ProviderConfig kubernetesV2ProviderConfig,
    KubernetesV1ProviderConfig kubernetesV1ProviderConfig,
    CatsModule catsModule
  ) {
    return new KubernetesCredentialsSynchronizer(
      accountCredentialsRepository,
      kubernetesConfigurationProperties,
      kubernetesSpinnakerKindMap,
      credentialFactory,
      catsModule,
      kubernetesV2ProviderConfig,
      kubernetesV1ProviderConfig
    )
  }

  @Bean
  @DependsOn('kubernetesCredentialsSynchronizer')
  List<? extends  KubernetesNamedAccountCredentials> kubernetesNamedAccountCredentials(
    AccountCredentialsRepository accountCredentialsRepository
  ) {
    accountCredentialsRepository.all.findAll{ credentials ->
      credentials instanceof KubernetesNamedAccountCredentials
    } as List<KubernetesNamedAccountCredentials>
  }

}
