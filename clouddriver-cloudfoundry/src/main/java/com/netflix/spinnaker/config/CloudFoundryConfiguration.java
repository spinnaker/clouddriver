/*
 * Copyright 2019 Pivotal, Inc.
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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentialsSynchronizer;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("cloudfoundry.enabled")
@ComponentScan("com.netflix.spinnaker.clouddriver.cloudfoundry")
public class CloudFoundryConfiguration {

  @Bean
  @RefreshScope
  CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties() {
    return new CloudFoundryConfigurationProperties();
  }

  @Bean
  CloudFoundryCredentialsSynchronizer cloudFoundryCredentialsSynchronizer(
      CloudFoundryProvider cloudFoundryProvider,
      CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties,
      AccountCredentialsRepository accountCredentialsRepository,
      CatsModule catsModule,
      Registry registry,
      CacheRepository cacheRepository) {
    return new CloudFoundryCredentialsSynchronizer(
        cloudFoundryProvider,
        cloudFoundryConfigurationProperties,
        accountCredentialsRepository,
        catsModule,
        registry,
        cacheRepository);
  }

  @Bean
  CloudFoundryProvider cloudFoundryProvider() {
    return new CloudFoundryProvider(Collections.newSetFromMap(new ConcurrentHashMap<>()));
  }

  @Bean
  OperationPoller cloudFoundryOperationPoller(CloudFoundryConfigurationProperties properties) {
    return new OperationPoller(
        properties.getAsyncOperationTimeoutMillisecondsDefault(),
        properties.getAsyncOperationMaxPollingIntervalMilliseconds());
  }
}
