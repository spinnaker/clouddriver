package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import java.util.Map;

public abstract class AbstractTencentCachingAgent implements CachingAgent, AccountAware {
  private AbstractTencentCachingAgent() {}

  public AbstractTencentCachingAgent(
      TencentNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    this.credentials = credentials;
    this.objectMapper = objectMapper;
    this.region = region;
    this.accountName = credentials.getName();
  }

  @Override
  public String getAgentType() {
    return getAccountName() + "/" + getRegion() + "/" + this.getClass().getSimpleName();
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public final String getRegion() {
    return region;
  }

  public final String getAccountName() {
    return accountName;
  }

  public final TencentNamedAccountCredentials getCredentials() {
    return credentials;
  }

  public final String getProviderName() {
    return providerName;
  }

  public final TypeReference<Map<String, Object>> getATTRIBUTES() {
    return ATTRIBUTES;
  }

  private ObjectMapper objectMapper;
  private String region;
  private String accountName;
  private TencentNamedAccountCredentials credentials;
  private final String providerName = TencentInfrastructureProvider.class.getName();
  private final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};
}
