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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.LogicalKind.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.INSTANCES;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUPS;
import static com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind.SERVER_GROUP_MANAGERS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys.InfrastructureCacheKey;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2Cluster;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2LoadBalancer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesV2ServerGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.data.KubernetesV2ServerGroupCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.ServerGroupHandler;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KubernetesV2ClusterProvider implements ClusterProvider<KubernetesV2Cluster> {
  private final KubernetesCacheUtils cacheUtils;
  private final KubernetesSpinnakerKindMap kindMap;

  @Autowired
  KubernetesV2ClusterProvider(KubernetesCacheUtils cacheUtils, KubernetesSpinnakerKindMap kindMap) {
    this.cacheUtils = cacheUtils;
    this.kindMap = kindMap;
  }

  @Override
  public Map<String, Set<KubernetesV2Cluster>> getClusters() {
    return groupByAccountName(loadClusters(cacheUtils.getAllKeys(CLUSTERS.toString())));
  }

  @Override
  public Map<String, Set<KubernetesV2Cluster>> getClusterSummaries(String application) {
    String applicationKey = Keys.ApplicationCacheKey.createKey(application);
    return groupByAccountName(
        loadClusterSummaries(
            cacheUtils.getRelationships(
                APPLICATIONS.toString(), applicationKey, CLUSTERS.toString())));
  }

  @Override
  public Map<String, Set<KubernetesV2Cluster>> getClusterDetails(String application) {
    String clusterGlobKey = Keys.ClusterCacheKey.createKey("*", application, "*");
    return groupByAccountName(
        loadClusters(cacheUtils.getAllDataMatchingPattern(CLUSTERS.toString(), clusterGlobKey)));
  }

  @Override
  public Set<KubernetesV2Cluster> getClusters(String application, String account) {
    String globKey = Keys.ClusterCacheKey.createKey(account, application, "*");
    return loadClusters(cacheUtils.getAllDataMatchingPattern(CLUSTERS.toString(), globKey));
  }

  @Override
  public KubernetesV2Cluster getCluster(String application, String account, String name) {
    return getCluster(application, account, name, true);
  }

  @Override
  public KubernetesV2Cluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    return cacheUtils
        .getSingleEntry(
            CLUSTERS.toString(), Keys.ClusterCacheKey.createKey(account, application, name))
        .map(
            entry -> {
              Collection<CacheData> clusterData = ImmutableList.of(entry);
              Set<KubernetesV2Cluster> result =
                  includeDetails ? loadClusters(clusterData) : loadClusterSummaries(clusterData);
              return result.iterator().next();
            })
        .orElse(null);
  }

  @Override
  public KubernetesV2ServerGroup getServerGroup(
      String account, String namespace, String name, boolean includeDetails) {
    Pair<KubernetesKind, String> parsedName;
    try {
      parsedName = KubernetesManifest.fromFullResourceName(name);
    } catch (IllegalArgumentException e) {
      return null;
    }

    KubernetesKind kind = parsedName.getLeft();
    String shortName = parsedName.getRight();
    String key = InfrastructureCacheKey.createKey(kind, account, namespace, shortName);
    List<String> relatedTypes =
        kindMap.translateSpinnakerKind(INSTANCES).stream()
            .map(KubernetesKind::toString)
            .collect(Collectors.toList());

    relatedTypes.addAll(
        kindMap.translateSpinnakerKind(LOAD_BALANCERS).stream()
            .map(KubernetesKind::toString)
            .collect(Collectors.toList()));

    Optional<CacheData> serverGroupData =
        cacheUtils.getSingleEntryWithRelationships(
            kind.toString(),
            key,
            RelationshipCacheFilter.include(relatedTypes.toArray(new String[0])));

    return serverGroupData
        .map(
            cd -> {
              List<CacheData> instanceData =
                  kindMap.translateSpinnakerKind(INSTANCES).stream()
                      .map(
                          k ->
                              cacheUtils.loadRelationshipsFromCache(
                                  ImmutableList.of(cd), k.toString()))
                      .flatMap(Collection::stream)
                      .collect(Collectors.toList());

              List<String> loadBalancerKeys =
                  kindMap.translateSpinnakerKind(LOAD_BALANCERS).stream()
                      .map(
                          k ->
                              cacheUtils.loadRelationshipsFromCache(
                                  ImmutableList.of(cd), k.toString()))
                      .flatMap(Collection::stream)
                      .map(CacheData::getId)
                      .collect(Collectors.toList());

              return serverGroupFromCacheData(
                  KubernetesV2ServerGroupCacheData.builder()
                      .serverGroupData(cd)
                      .instanceData(instanceData)
                      .loadBalancerKeys(loadBalancerKeys)
                      .build());
            })
        .orElse(null);
  }

  @Override
  public KubernetesV2ServerGroup getServerGroup(String account, String namespace, String name) {
    return getServerGroup(account, namespace, name, true);
  }

  @Override
  public String getCloudProviderId() {
    return KubernetesCloudProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return true;
  }

  private Map<String, Set<KubernetesV2Cluster>> groupByAccountName(
      Collection<KubernetesV2Cluster> clusters) {
    return clusters.stream().collect(groupingBy(KubernetesV2Cluster::getAccountName, toSet()));
  }

  private Set<KubernetesV2Cluster> loadClusterSummaries(Collection<CacheData> clusterData) {
    return clusterData.stream()
        .map(clusterDatum -> new KubernetesV2Cluster(clusterDatum.getId()))
        .collect(toSet());
  }

  private Set<KubernetesV2Cluster> loadClusters(Collection<CacheData> clusterData) {
    // TODO(lwander) possible optimization: store lb relationships in cluster object to cut down on
    // number of loads here.
    List<CacheData> serverGroupData =
        kindMap.translateSpinnakerKind(SERVER_GROUPS).stream()
            .map(kind -> cacheUtils.loadRelationshipsFromCache(clusterData, kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    List<CacheData> loadBalancerData =
        kindMap.translateSpinnakerKind(LOAD_BALANCERS).stream()
            .map(kind -> cacheUtils.loadRelationshipsFromCache(serverGroupData, kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    List<CacheData> instanceData =
        kindMap.translateSpinnakerKind(INSTANCES).stream()
            .map(kind -> cacheUtils.loadRelationshipsFromCache(serverGroupData, kind.toString()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    Map<String, List<CacheData>> clusterToServerGroups = new HashMap<>();
    for (CacheData serverGroupDatum : serverGroupData) {
      Collection<String> clusterKeys = serverGroupDatum.getRelationships().get(CLUSTERS.toString());
      if (clusterKeys == null || clusterKeys.size() != 1) {
        log.warn("Malformed cache, server group stored without cluster");
        continue;
      }

      String clusterKey = clusterKeys.iterator().next();
      List<CacheData> storedData =
          clusterToServerGroups.getOrDefault(clusterKey, new ArrayList<>());
      storedData.add(serverGroupDatum);
      clusterToServerGroups.put(clusterKey, storedData);
    }

    Map<String, List<String>> serverGroupToServerGroupManagerKeys = new HashMap<>();
    for (CacheData serverGroupDatum : serverGroupData) {
      serverGroupToServerGroupManagerKeys.put(
          serverGroupDatum.getId(),
          kindMap.translateSpinnakerKind(SERVER_GROUP_MANAGERS).stream()
              .map(kind -> serverGroupDatum.getRelationships().get(kind.toString()))
              .filter(Objects::nonNull)
              .flatMap(Collection::stream)
              .collect(Collectors.toList()));
    }

    ImmutableMultimap<String, CacheData> serverGroupToLoadBalancers =
        cacheUtils.mapByRelationship(loadBalancerData, SERVER_GROUPS);
    ImmutableMultimap<String, CacheData> serverGroupToInstances =
        cacheUtils.mapByRelationship(instanceData, SERVER_GROUPS);
    ImmutableMultimap<String, CacheData> loadBalancerToServerGroups =
        cacheUtils.mapByRelationship(serverGroupData, LOAD_BALANCERS);

    return clusterData.stream()
        .map(
            clusterDatum -> {
              List<CacheData> clusterServerGroups =
                  clusterToServerGroups.getOrDefault(clusterDatum.getId(), new ArrayList<>());

              List<KubernetesV2ServerGroup> serverGroups =
                  clusterServerGroups.stream()
                      .map(
                          cd ->
                              serverGroupFromCacheData(
                                  KubernetesV2ServerGroupCacheData.builder()
                                      .serverGroupData(cd)
                                      .instanceData(serverGroupToInstances.get(cd.getId()))
                                      .loadBalancerKeys(
                                          serverGroupToLoadBalancers.get(cd.getId()).stream()
                                              .map(CacheData::getId)
                                              .collect(toImmutableList()))
                                      .serverGroupManagerKeys(
                                          serverGroupToServerGroupManagerKeys.getOrDefault(
                                              cd.getId(), new ArrayList<>()))
                                      .build()))
                      .collect(Collectors.toList());

              List<KubernetesV2LoadBalancer> loadBalancers =
                  clusterServerGroups.stream()
                      .map(CacheData::getId)
                      .map(serverGroupToLoadBalancers::get)
                      .flatMap(Collection::stream)
                      .map(
                          cd ->
                              KubernetesV2LoadBalancer.fromCacheData(
                                  cd,
                                  loadBalancerToServerGroups.get(cd.getId()),
                                  serverGroupToInstances))
                      .filter(Objects::nonNull)
                      .collect(Collectors.toList());

              return new KubernetesV2Cluster(clusterDatum.getId(), serverGroups, loadBalancers);
            })
        .collect(toSet());
  }

  private final ServerGroupHandler DEFAULT_SERVER_GROUP_HANDLER = new ServerGroupHandler() {};

  @Nonnull
  private KubernetesV2ServerGroup serverGroupFromCacheData(
      @Nonnull KubernetesV2ServerGroupCacheData cacheData) {
    KubernetesHandler handler = cacheUtils.getHandler(cacheData);
    ServerGroupHandler serverGroupHandler =
        handler instanceof ServerGroupHandler
            ? (ServerGroupHandler) handler
            : DEFAULT_SERVER_GROUP_HANDLER;
    return serverGroupHandler.fromCacheData(cacheData);
  }
}
