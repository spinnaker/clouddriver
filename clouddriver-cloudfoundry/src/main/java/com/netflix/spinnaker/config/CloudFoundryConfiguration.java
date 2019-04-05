/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProviderSynchronizer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentialsSynchronizer;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("cloudfoundry.enabled")
@ComponentScan("com.netflix.spinnaker.clouddriver.cloudfoundry")
public class CloudFoundryConfiguration {

  @Bean
  CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties() {
    return new CloudFoundryConfigurationProperties();
  }

  @Bean
  CloudFoundryCredentialsSynchronizer cloudFoundryCredentialsSynchronizer(CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
                                                                          AccountCredentialsRepository accountCredentialsRepository,
                                                                          CatsModule catsModule) {
    return new CloudFoundryCredentialsSynchronizer(cloudFoundryConfigurationProperties, accountCredentialsRepository, catsModule);
  }

  @Bean
  CloudFoundryProvider cloudFoundryProvider() {
    return new CloudFoundryProvider(Collections.newSetFromMap(new ConcurrentHashMap<>()));
  }

  @Bean
  CloudFoundryProviderSynchronizer cloudFoundryProviderSynchronizer(CloudFoundryProvider cloudFoundryProvider,
                                                                    AccountCredentialsRepository accountCredentialsRepository) {
    return new CloudFoundryProviderSynchronizer(cloudFoundryProvider, accountCredentialsRepository);
  }

  @Bean
  OperationPoller cloudFoundryOperationPoller(CloudFoundryConfigurationProperties properties) {
    return new OperationPoller(
      properties.getAsyncOperationTimeoutMillisecondsDefault(),
      properties.getAsyncOperationMaxPollingIntervalMilliseconds()
    );
  }
}
