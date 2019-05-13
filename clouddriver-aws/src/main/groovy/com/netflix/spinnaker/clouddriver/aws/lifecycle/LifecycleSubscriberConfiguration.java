/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  LaunchFailureConfigurationProperties.class,
  InstanceTerminationConfigurationProperties.class
})
class LifecycleSubscriberConfiguration {

  @Bean
  @ConditionalOnProperty("aws.lifecycle-subscribers.launch-failure.enabled")
  LaunchFailureNotificationAgentProvider launchFailureNotificationAgentProvider(
      @Qualifier("amazonObjectMapper") ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      AccountCredentialsProvider accountCredentialsProvider,
      LaunchFailureConfigurationProperties properties,
      EntityTagger entityTagger) {
    return new LaunchFailureNotificationAgentProvider(
        objectMapper, amazonClientProvider, accountCredentialsProvider, properties, entityTagger);
  }
}
