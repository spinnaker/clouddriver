/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.elasticsearch.aws.ElasticSearchAmazonCachingAgentProvider
import com.netflix.spinnaker.clouddriver.elasticsearch.aws.ElasticSearchAmazonScalingPolicyAgentProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.kork.core.RetrySupport
import io.searchbox.client.JestClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ClusterScalingPolicyTaggingAgentProperties::class)
open class ElasticSearchAmazonConfig {

  @Bean
  @ConditionalOnProperty("elasticSearch.caching.enabled")
  open fun elasticSearchAmazonCachingAgentProvider(objectMapper: ObjectMapper,
                                                   jestClient: JestClient,
                                                   retrySupport: RetrySupport,
                                                   registry: Registry,
                                                   amazonClientProvider: AmazonClientProvider,
                                                   accountCredentialsProvider: AccountCredentialsProvider,
                                                   entityTagger: EntityTagger) =
    ElasticSearchAmazonCachingAgentProvider(
      objectMapper,
      jestClient,
      retrySupport,
      registry,
      amazonClientProvider,
      accountCredentialsProvider
    )

  @Bean
  @ConditionalOnProperty("elasticSearch.scalingPolicies.enabled")
  open fun elasticSearchAmazonScalingPolicyAgentProvider(objectMapper: ObjectMapper,
                                                         retrySupport: RetrySupport,
                                                         registry: Registry,
                                                         amazonClientProvider: AmazonClientProvider,
                                                         accountCredentialsProvider: AccountCredentialsProvider,
                                                         entityTagger: EntityTagger,
                                                         properties: ClusterScalingPolicyTaggingAgentProperties) =
    ElasticSearchAmazonScalingPolicyAgentProvider(
      objectMapper,
      retrySupport,
      registry,
      amazonClientProvider,
      accountCredentialsProvider,
      entityTagger,
      properties
    )
}
