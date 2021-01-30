/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.amazonaws.services.ec2.model.GetConsoleOutputRequest
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonInstance
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.core.provider.agent.ExternalHealthProvider
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.credentials.CredentialsRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*

@Component
class AmazonInstanceProvider implements InstanceProvider<AmazonInstance, String> {

  final String cloudProvider = AmazonCloudProvider.ID
  private final Cache cacheView

  @Autowired(required = false)
  List<ExternalHealthProvider> externalHealthProviders

  @Autowired
  AmazonInstanceProvider(Cache cacheView) {
    this.cacheView = cacheView
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  CredentialsRepository<NetflixAmazonCredentials> credentialsRepository

  @Override
  AmazonInstance getInstance(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region))
    if (!instanceEntry) {
      return null
    }
    AmazonInstance instance = new AmazonInstance(instanceEntry.attributes)
    instance.name = id
    if (instanceEntry.relationships[SERVER_GROUPS.ns] && !instanceEntry.relationships[SERVER_GROUPS.ns].empty) {
      Map serverGroup = Keys.parse(instanceEntry.relationships[SERVER_GROUPS.ns].iterator().next())
      instance.serverGroup = serverGroup.serverGroup
      instance.cluster = serverGroup.cluster
    }
    def healthKeys = []
    if (instanceEntry.relationships[HEALTH.ns]) {
      healthKeys.addAll(instanceEntry.relationships[HEALTH.ns])
    }
    externalHealthProviders.each { externalHealthProvider ->
      externalHealthProvider.agents.each { externalHealthAgent ->
        healthKeys << Keys.getInstanceHealthKey(instance.name, account, region, externalHealthAgent.healthId)
      }
    }
    healthKeys.unique().each { key ->
      def instanceHealth = cacheView.getAll(HEALTH.ns, key)
      if (instanceHealth) {
        instance.health.addAll(instanceHealth*.attributes)
      }
    }
    instance
  }

  String getConsoleOutput(String account, String region, String id) {
    def credentials = credentialsRepository.getOne(account)
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }
    amazonClientProvider.getAmazonEC2(credentials, region, true).getConsoleOutput(new GetConsoleOutputRequest(id)).decodedOutput
  }

}
