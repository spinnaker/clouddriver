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
package com.netflix.spinnaker.clouddriver.elasticsearch.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.config.ClusterScalingPolicyTaggingAgentProperties
import com.netflix.spinnaker.kork.core.RetrySupport

open class ElasticSearchAmazonScalingPolicyAgentProvider(
  private val objectMapper: ObjectMapper,
  private val retrySupport: RetrySupport,
  private val registry: Registry,
  private val amazonClientProvider: AmazonClientProvider,
  private val accountCredentialsProvider: AccountCredentialsProvider,
  private val entityTagger : EntityTagger,
  private val properties: ClusterScalingPolicyTaggingAgentProperties
) : AgentProvider {

  override fun supports(providerName: String) =
    providerName.equals(AwsProvider.PROVIDER_NAME, ignoreCase = true)

  override fun agents(): Collection<Agent> {
    val credentials = accountCredentialsProvider.all.filterIsInstance<NetflixAmazonCredentials>()

    return listOf(
      ClusterScalingPolicyTaggingAgent(
        retrySupport,
        registry,
        amazonClientProvider,
        credentials,
        entityTagger,
        objectMapper,
        properties
      )
    )
  }
}
