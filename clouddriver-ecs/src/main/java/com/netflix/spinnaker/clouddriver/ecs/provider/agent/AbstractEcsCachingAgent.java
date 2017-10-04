package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public abstract class AbstractEcsCachingAgent<T> implements CachingAgent, OnDemandAgent {
  protected AmazonClientProvider amazonClientProvider;
  protected AWSCredentialsProvider awsCredentialsProvider;
  protected String region;
  protected String accountName;
  protected OnDemandMetricsSupport metricsSupport;

  public AbstractEcsCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, EcsCloudProvider.ID + ":" + EcsCloudProvider.ID + ":${OnDemandAgent.OnDemandType.ServerGroup}");
  }

  protected abstract List<T> getItems(AmazonECS ecs, ProviderCache providerCache);

  protected abstract CacheResult buildCacheResult(List<T> items);

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType();
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new LinkedList<>();
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(EcsCloudProvider.ID);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);
    List<T> items = getItems(ecs, providerCache);
    return buildCacheResult(items);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!data.get("account").equals(accountName) || !data.get("region").equals(region)) {
      return null;
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);

    List<T> items = metricsSupport.readData(new Closure<List<T>>(this, this) {
      public List<T> doCall() {
        return getItems(ecs, providerCache);
      }
    });

    storeOnDemand(providerCache, data);

    CacheResult cacheResult = metricsSupport.transformData(new Closure<CacheResult>(this, this) {
      public CacheResult doCall() {
        return buildCacheResult(items);
      }
    });


    Collection<String> typeStrings = new LinkedList<>();
    for (AgentDataType agentDataType : getProvidedDataTypes()) {
      typeStrings.add(agentDataType.toString());
    }

    OnDemandResult result = new OnDemandResult();
    result.setAuthoritativeTypes(typeStrings);
    result.setCacheResult(cacheResult);
    result.setSourceAgentType(getAgentType());

    return result;
  }

  protected void storeOnDemand(ProviderCache providerCache, Map<String, ?> data) {
    // TODO: Overwrite if needed.
  }
}
