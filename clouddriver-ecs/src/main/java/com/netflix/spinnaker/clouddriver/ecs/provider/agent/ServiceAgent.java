package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

public class ServiceAgent implements CachingAgent {
  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.emptyList();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    //TODO: Implement the real functionality, not examples.
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();

    String key = Keys.getServiceKey("continuous-delivery-ecs", "us-west-2","app-stack-detail-v1337");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("test-attribute-key", "test-attribute-value");

    CacheData dataPoint = new DefaultCacheData(key, 600, attributes, Collections.emptyMap());

    Collection<CacheData> dataPoints = new LinkedList<>();
    dataPoints.add(dataPoint);

    dataMap.put(SERVICES.toString(), dataPoints);
    return new DefaultCacheResult(dataMap);
  }

  @Override
  public String getAgentType() {
    return ServiceAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }
}
