package com.netflix.spinnaker.clouddriver.tencent.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials

abstract class AbstractTencentCachingAgent implements CachingAgent, AccountAware {
  final ObjectMapper objectMapper
  final String region
  final String accountName
  final TencentNamedAccountCredentials credentials
  final String providerName = TencentInfrastructureProvider.name

  final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  AbstractTencentCachingAgent(TencentNamedAccountCredentials credentials,
                              ObjectMapper objectMapper,
                              String region) {
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.region = region
    this.accountName = credentials.name
  }

  @Override
  String getAgentType() {
    return "$accountName/$region/${this.class.simpleName}"
  }
}
