/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultJsonCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class KubernetesOnDemandCachingAgent extends KubernetesCachingAgent
    implements OnDemandAgent {
  private static final Logger log = LoggerFactory.getLogger(KubernetesOnDemandCachingAgent.class);
  @Getter protected final OnDemandMetricsSupport metricsSupport;

  protected static final String ON_DEMAND_TYPE = "onDemand";
  private static final String CACHE_TIME_KEY = "cacheTime";
  private static final String PROCESSED_COUNT_KEY = "processedCount";
  private static final String PROCESSED_TIME_KEY = "processedTime";
  private static final String CACHE_RESULTS_KEY = "cacheResults";

  protected KubernetesOnDemandCachingAgent(
      KubernetesNamedAccountCredentials namedAccountCredentials,
      ObjectMapper objectMapper,
      Registry registry,
      int agentIndex,
      int agentCount,
      Long agentInterval) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount, agentInterval);

    metricsSupport =
        new OnDemandMetricsSupport(
            registry, this, KubernetesCloudProvider.ID + ":" + OnDemandType.Manifest);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info(getAgentType() + ": agent is starting");
    Map<String, Object> details = defaultIntrospectionDetails();

    Long start = System.currentTimeMillis();
    Map<KubernetesKind, List<KubernetesManifest>> primaryResource = loadPrimaryResourceList();

    details.put("timeSpentInKubectlMs", System.currentTimeMillis() - start);

    List<String> primaryKeys =
        primaryResource.values().stream()
            .flatMap(Collection::stream)
            .map(mf -> Keys.InfrastructureCacheKey.createKey(mf, accountName))
            .collect(Collectors.toList());

    List<CacheData> keepInOnDemand = new ArrayList<>();
    List<CacheData> evictFromOnDemand = new ArrayList<>();

    Collection<String> existingKeys =
        providerCache.existingIdentifiers(ON_DEMAND_TYPE, primaryKeys);

    providerCache
        .getAll(ON_DEMAND_TYPE, existingKeys)
        .forEach(
            cd -> {
              // can't be a ternary op due to restrictions on non-statement expressions in lambdas
              if (shouldKeepInOnDemand(start, cd)) {
                keepInOnDemand.add(cd);
              } else {
                evictFromOnDemand.add(cd);
              }
              processOnDemandEntry(cd);
            });

    // sort by increasing cache time to ensure newest entries are first
    keepInOnDemand.sort(Comparator.comparing(a -> ((Long) a.getAttributes().get(CACHE_TIME_KEY))));

    // first build the cache result, then decide which entries to overwrite with on demand data
    CacheResult result = buildCacheResult(primaryResource);
    Map<String, Collection<CacheData>> cacheResults = result.getCacheResults();

    for (CacheData onDemandData : keepInOnDemand) {
      if (!shouldOverwriteUsingOnDemand(start, onDemandData)) {
        continue;
      }

      String onDemandKey = onDemandData.getId();
      log.info(
          "{}: On demand entry '{}' is overwriting load data entry", getAgentType(), onDemandKey);

      String onDemandResultsJson = (String) onDemandData.getAttributes().get(CACHE_RESULTS_KEY);

      log.debug(
          "{}: On demand entry contents overwriting load data entry: {}",
          getAgentType(),
          onDemandResultsJson);
      Map<String, List<DefaultJsonCacheData>> onDemandResults;
      try {
        onDemandResults =
            objectMapper.readValue(
                onDemandResultsJson,
                new TypeReference<Map<String, List<DefaultJsonCacheData>>>() {});
      } catch (IOException e) {
        log.error("Failure parsing stored on demand data for '{}'", onDemandKey, e);
        continue;
      }

      mergeCacheResults(cacheResults, onDemandResults);
    }

    cacheResults.put(ON_DEMAND_TYPE, keepInOnDemand);
    Map<String, Collection<String>> evictionResults =
        new ImmutableMap.Builder<String, Collection<String>>()
            .put(
                ON_DEMAND_TYPE,
                evictFromOnDemand.stream().map(CacheData::getId).collect(Collectors.toList()))
            .build();

    return new DefaultCacheResult(cacheResults, evictionResults, details);
  }

  protected void mergeCacheResults(
      Map<String, Collection<CacheData>> current,
      Map<String, ? extends Collection<? extends CacheData>> added) {
    for (String group : added.keySet()) {
      Collection<CacheData> currentByGroup = current.get(group);
      Collection<? extends CacheData> addedByGroup = added.get(group);

      currentByGroup = currentByGroup == null ? new ArrayList<>() : currentByGroup;
      addedByGroup = addedByGroup == null ? new ArrayList<>() : addedByGroup;

      for (CacheData addedCacheData : addedByGroup) {
        CacheData mergedEntry =
            currentByGroup.stream()
                .filter(cd -> cd.getId().equals(addedCacheData.getId()))
                .findFirst()
                .flatMap(
                    cd ->
                        Optional.of(
                            KubernetesCacheDataConverter.mergeCacheData(cd, addedCacheData)))
                .orElse(addedCacheData);

        currentByGroup.removeIf(cd -> cd.getId().equals(addedCacheData.getId()));
        currentByGroup.add(mergedEntry);
      }

      current.put(group, currentByGroup);
    }
  }

  private boolean shouldOverwriteUsingOnDemand(Long startTime, CacheData onDemandEntry) {
    Map<String, Object> attributes = onDemandEntry.getAttributes();
    Long cacheTime = (Long) attributes.get(CACHE_TIME_KEY);

    return cacheTime != null && cacheTime >= startTime;
  }

  private void processOnDemandEntry(CacheData onDemandEntry) {
    Map<String, Object> attributes = onDemandEntry.getAttributes();
    Integer processedCount = (Integer) attributes.get(PROCESSED_COUNT_KEY);
    Long processedTime = System.currentTimeMillis();

    processedCount = processedCount == null ? 0 : processedCount;
    processedCount += 1;

    attributes.put(PROCESSED_TIME_KEY, processedTime);
    attributes.put(PROCESSED_COUNT_KEY, processedCount);
  }

  private boolean shouldKeepInOnDemand(Long lastFullRefresh, CacheData onDemandEntry) {
    Map<String, Object> attributes = onDemandEntry.getAttributes();
    Long cacheTime = (Long) attributes.get(CACHE_TIME_KEY);
    Integer processedCount = (Integer) attributes.get(PROCESSED_COUNT_KEY);

    cacheTime = cacheTime == null ? 0L : cacheTime;
    processedCount = processedCount == null ? 0 : processedCount;

    return cacheTime >= lastFullRefresh || processedCount < 2;
  }

  @Override
  public OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    return null;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return false;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    return ImmutableList.of();
  }
}
