/*
 * Copyright 2022 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.common.DateParseHelper;
import com.netflix.spinnaker.clouddriver.alicloud.common.HealthHelper;
import com.netflix.spinnaker.clouddriver.alicloud.common.Sets;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudCluster;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudInstance;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudServerGroup;
import com.netflix.spinnaker.clouddriver.alicloud.provider.agent.AliCloudLoadBalancerCachingAgent.LoadBalancerCacheBuilder;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity;
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudClusterProvider
    implements ClusterProvider<AliCloudCluster>, ServerGroupProvider {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  private final AliCloudProvider provider;

  @Autowired
  public AliCloudClusterProvider(
      ObjectMapper objectMapper, Cache cacheView, AliCloudProvider provider) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
    this.provider = provider;
  }

  @Override
  public Map<String, Set<AliCloudCluster>> getClusterDetails(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));
    if (application == null) {
      return Collections.emptyMap();
    }
    Collection<String> clusterKeys = application.getRelationships().get(CLUSTERS.ns);
    Collection<CacheData> clusterCaches = cacheView.getAll(CLUSTERS.ns, clusterKeys);

    List<AliCloudCluster> aliCloudClusters =
        clusterCaches.stream()
            .map(clusterCache -> translateClusters(clusterCache, true))
            .collect(Collectors.toList());

    Map<String, Set<AliCloudCluster>> resultMap = new HashMap<>(16);
    resultMap.put(applicationName, new HashSet<>(aliCloudClusters));
    return resultMap;
  }

  @Override
  public AliCloudCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    String clusterKey = Keys.getClusterKey(name, application, account);
    CacheData cluster = cacheView.get(CLUSTERS.ns, clusterKey);
    if (cluster == null) {
      return null;
    } else {
      return translateClusters(cluster, includeDetails);
    }
  }

  private AliCloudCluster translateClusters(CacheData clusterData, boolean includeDetails) {
    if (clusterData == null) {
      return null;
    }
    Set<AliCloudServerGroup> serverGroups = new HashSet<>();
    Set<AliCloudLoadBalancer> loadBalancers = new HashSet<>();

    String application = (String) clusterData.getAttributes().get("application");
    String accountName = (String) clusterData.getAttributes().get("accountName");
    if (!includeDetails) {
      return new AliCloudCluster(application, accountName, serverGroups, loadBalancers);
    }

    Map<String, Collection<String>> relationships = clusterData.getRelationships();
    Collection<String> serverGroupKeys = relationships.get(SERVER_GROUPS.ns);
    Collection<CacheData> serverGroupCaches = cacheView.getAll(SERVER_GROUPS.ns, serverGroupKeys);
    if (serverGroupCaches != null) {
      serverGroups =
          serverGroupCaches.stream().map(this::buildServerGroup).collect(Collectors.toSet());
    }

    Collection<String> loadBalancerKeys = relationships.get(LOAD_BALANCERS.ns);
    if (loadBalancerKeys != null && loadBalancerKeys.size() > 0) {
      Collection<CacheData> loadBalancerCacheData =
          cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys.toArray(new String[] {}));
      loadBalancers =
          loadBalancerCacheData.stream()
              .map(cacheData -> LoadBalancerCacheBuilder.buildLoadBalancer(cacheData, objectMapper))
              .collect(Collectors.toSet());
    }

    return new AliCloudCluster(application, accountName, serverGroups, loadBalancers);
  }

  @Override
  public AliCloudServerGroup getServerGroup(
      String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);

    if (serverGroupData == null) {
      return null;
    }

    return buildServerGroup(serverGroupData);
  }

  private AliCloudServerGroup buildServerGroup(CacheData serverGroupCache) {
    Map<String, Object> attributes = serverGroupCache.getAttributes();

    Collection<String> allHealthyKeys = cacheView.getIdentifiers(HEALTH.ns);

    AliCloudServerGroup serverGroup = new AliCloudServerGroup();

    serverGroup.setType(AliCloudProvider.ID);
    serverGroup.setName(String.valueOf(attributes.get("name")));
    serverGroup.setCloudProvider(AliCloudProvider.ID);
    serverGroup.setRegion(String.valueOf(attributes.get("region")));

    Map<String, Object> scalingGroup = (Map) attributes.get("scalingGroup");
    serverGroup.setResult(scalingGroup);

    String scalingGroupLifecycleState = (String) scalingGroup.get("lifecycleState");
    serverGroup.setDisabled(!"Active".equals(scalingGroupLifecycleState));

    String creationTime = String.valueOf(scalingGroup.get("creationTime"));
    serverGroup.setCreationTime(creationTime);
    try {
      Date d = DateParseHelper.parseUTCTime(creationTime);
      serverGroup.setCreatedTime(d.getTime());
    } catch (ParseException e) {
      e.printStackTrace();
    }
    List<Map> instances = (List<Map>) attributes.get("instances");
    Set<String> loadBalancerIds = new HashSet<>();
    if (scalingGroup.containsKey("loadBalancerIds")) {
      loadBalancerIds.addAll((List<String>) scalingGroup.get("loadBalancerIds"));
      ArrayList<Map> vServerGroups = (ArrayList<Map>) scalingGroup.get("vserverGroups");
      if (vServerGroups != null) {
        vServerGroups.forEach(
            vServerGroup -> {
              loadBalancerIds.add(String.valueOf(vServerGroup.get("loadBalancerId")));
            });
      }
    }
    serverGroup.setLoadBalancers(loadBalancerIds);

    serverGroup.setInstances(
        instances.stream()
            .map(
                instance ->
                    buildAliCloudInstance(
                        allHealthyKeys, scalingGroupLifecycleState, loadBalancerIds, instance))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));

    // build capacity
    Capacity capacity = new Capacity();
    Object maxSize = scalingGroup.get("maxSize");
    Object minSize = scalingGroup.get("minSize");

    capacity.setMax((Integer) maxSize);
    capacity.setMin((Integer) minSize);
    if (instances.size() >= (Integer) minSize) {
      capacity.setDesired(instances.size());
    } else {
      capacity.setDesired((Integer) minSize);
    }
    serverGroup.setCapacity(capacity);
    serverGroup.setInstanceCounts(buildInstanceCounts(serverGroup));
    serverGroup.setResult(serverGroupCache.getAttributes());

    // build image info
    Map<String, Object> scalingConfiguration = (Map) attributes.get("scalingConfiguration");
    serverGroup.setLaunchConfig(scalingConfiguration);
    Map<String, Object> image = new HashMap<>();
    image.put("name", scalingConfiguration.get("imageId"));
    image.put("imageId", scalingConfiguration.get("imageId"));
    Map buildInfo = new HashMap();
    buildInfo.put("imageId", scalingConfiguration.get("imageId"));
    serverGroup.setImage(image);
    serverGroup.setBuildInfo(buildInfo);
    serverGroup.setSecurityGroups(
        Sets.ofModifiable(String.valueOf(scalingConfiguration.get("securityGroupId"))));

    return serverGroup;
  }

  private AliCloudInstance buildAliCloudInstance(
      Collection<String> allHealthyKeys,
      String scalingGroupLifecycleState,
      Set<String> loadBalancerIds,
      Map instance) {
    String instanceId = String.valueOf(instance.get("instanceId"));
    if (StringUtils.isBlank(instanceId)) {
      return null;
    }

    String zone = (String) instance.get("creationType");

    List<String> healthKeys =
        allHealthyKeys.stream()
            .filter(k -> HealthHelper.healthyStateMatcher(k, loadBalancerIds, instanceId))
            .collect(Collectors.toList());
    Collection<CacheData> healthDatas =
        cacheView.getAll(HEALTH.ns, healthKeys.toArray(new String[] {}));
    HealthState healthState =
        HealthHelper.genInstanceHealthState(
            scalingGroupLifecycleState, String.valueOf(instance.get("healthStatus")), healthDatas);

    List<Map<String, Object>> health = new ArrayList<>();
    Map<String, Object> m = new HashMap<>();
    m.put("type", provider.getDisplayName());
    m.put("healthClass", "platform");
    m.put("state", healthState);
    health.add(m);

    return new AliCloudInstance(instanceId, null, zone, healthState, health);
  }

  private ServerGroup.InstanceCounts buildInstanceCounts(AliCloudServerGroup serverGroup) {
    Map<String, Integer> countMap = new HashMap<>(16);
    ServerGroup.InstanceCounts instanceCounts = new ServerGroup.InstanceCounts();
    instanceCounts.setTotal(serverGroup.getInstances().size());
    serverGroup
        .getInstances()
        .forEach(
            instance -> {
              if (instance.getHealthState().equals(HealthState.Up)) {
                countMap.put("up", countMap.getOrDefault("up", 0) + 1);
              }
              if (instance.getHealthState().equals(HealthState.Down)) {
                countMap.put("down", countMap.getOrDefault("down", 0) + 1);
              }
              if (instance.getHealthState().equals(HealthState.Unknown)) {
                countMap.put("unKnown", countMap.getOrDefault("unKnown", 0) + 1);
              }
            });
    instanceCounts.setUp(countMap.getOrDefault("up", 0));
    instanceCounts.setDown(countMap.getOrDefault("down", 0));
    instanceCounts.setUnknown(countMap.getOrDefault("unKnown", 0));
    return instanceCounts;
  }

  @Override
  public ServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
    if (serverGroupData == null) {
      return null;
    }
    return buildServerGroup(serverGroupData);
  }

  @Override
  public String getCloudProviderId() {
    return AliCloudProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

  @Override
  public Collection<String> getServerGroupIdentifiers(String account, String region) {
    account = Optional.ofNullable(account).orElse("*");
    region = Optional.ofNullable(region).orElse("*");
    return cacheView.filterIdentifiers(
        SERVER_GROUPS.ns, Keys.getServerGroupKey("*", "*", account, region));
  }

  @Override
  public String buildServerGroupIdentifier(String account, String region, String serverGroupName) {
    return Keys.getServerGroupKey(serverGroupName, account, region);
  }

  @Override
  public Map<String, Set<AliCloudCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns);
    Collection<AliCloudCluster> clusters =
        clusterData.stream().map(d -> translateClusters(d, false)).collect(Collectors.toList());

    return mapResponse(clusters);
  }

  @Override
  public Map<String, Set<AliCloudCluster>> getClusterSummaries(String application) {
    return getClusterDetails(application);
  }

  @Override
  public Set<AliCloudCluster> getClusters(String application, String account) {
    Map<String, Set<AliCloudCluster>> clusterDetails = getClusterDetails(application);
    return clusterDetails.isEmpty() ? Collections.emptySet() : clusterDetails.get(account);
  }

  @Override
  public Set<AliCloudCluster> getClusters(
      String application, String account, boolean includeDetails) {
    Map<String, Set<AliCloudCluster>> clusterDetails = getClusterDetails(application);
    return clusterDetails.isEmpty() ? Collections.emptySet() : clusterDetails.get(account);
  }

  @Override
  public AliCloudCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account));

    return translateClusters(cluster, true);
  }

  private static Map<String, Set<AliCloudCluster>> mapResponse(
      Collection<AliCloudCluster> clusters) {
    Map<String, Set<AliCloudCluster>> results =
        clusters.stream()
            .collect(Collectors.groupingBy(AliCloudCluster::getAccountName, Collectors.toSet()));
    // Map<String, Set<AliCloudCluster>> results =
    // clusters.stream().collect(Collectors.toMap(AliCloudCluster::getAccountName, p ->
    // Collections.singleton(p), (x, y) ->{
    //  Set<AliCloudCluster> set = new HashSet<>(x);
    //  set.addAll(y);
    //  return set;
    // }));
    return results;
  }
}
