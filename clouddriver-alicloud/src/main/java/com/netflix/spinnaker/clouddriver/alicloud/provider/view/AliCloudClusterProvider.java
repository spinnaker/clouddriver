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
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudCluster;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudInstance;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudServerGroup;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity;
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    Map<String, Set<AliCloudCluster>> resultMap = new HashMap<>(16);
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));
    if (application == null) {
      return null;
    }
    Set<CacheData> clusterData = new HashSet<>();
    Collection<String> clusterKeys = application.getRelationships().get(CLUSTERS.ns);
    for (String clusterKey : clusterKeys) {
      CacheData clusterCache = cacheView.get(CLUSTERS.ns, clusterKey);
      clusterData.add(clusterCache);
    }
    List<AliCloudCluster> aliCloudClusters = translateClusters(clusterData, true);

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
      Set<CacheData> clusterData = new HashSet<>();
      clusterData.add(cluster);
      List<AliCloudCluster> aliCloudClusters = translateClusters(clusterData, true);
      return aliCloudClusters.get(0);
    }
  }

  private List<AliCloudCluster> translateClusters(
      Collection<CacheData> clusterData, boolean includeDetails) {
    List<AliCloudCluster> set = new ArrayList<>();
    Collection<String> allHealthyKeys = cacheView.getIdentifiers(HEALTH.ns);
    for (CacheData clusterCache : clusterData) {
      String application = (String) clusterCache.getAttributes().get("application");
      Map<String, Collection<String>> relationships = clusterCache.getRelationships();
      Collection<String> serverGroupKeys = relationships.get(SERVER_GROUPS.ns);
      Set<AliCloudServerGroup> serverGroups = new HashSet<>();
      Set<AliCloudLoadBalancer> loadBalancers = new HashSet<>();
      String accountName = "";
      for (String serverGroupKey : serverGroupKeys) {
        CacheData serverGroupCache = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
        Map<String, Object> attributes = serverGroupCache.getAttributes();
        accountName = String.valueOf(attributes.get("account"));
        serverGroups.add(bulidServerGroup(allHealthyKeys, serverGroupCache));
      }
      AliCloudCluster cluster =
          new AliCloudCluster(
              application, AliCloudProvider.ID, accountName, serverGroups, loadBalancers);
      set.add(cluster);
    }
    return set;
  }

  @Override
  public AliCloudServerGroup getServerGroup(
      String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
    Collection<String> allHealthyKeys = cacheView.getIdentifiers(HEALTH.ns);
    if (serverGroupData == null) {
      return null;
    }
    return bulidServerGroup(allHealthyKeys, serverGroupData);
  }

  private AliCloudServerGroup bulidServerGroup(
      Collection<String> allHealthyKeys, CacheData serverGroupCache) {
    Map<String, Object> attributes = serverGroupCache.getAttributes();

    AliCloudServerGroup serverGroup = new AliCloudServerGroup();

    Set<AliCloudInstance> s = new HashSet<>();

    serverGroup.setType(AliCloudProvider.ID);
    serverGroup.setName(String.valueOf(attributes.get("name")));
    serverGroup.setCloudProvider(AliCloudProvider.ID);
    serverGroup.setRegion(String.valueOf(attributes.get("region")));

    Map<String, Object> scalingGroup = (Map) attributes.get("scalingGroup");
    serverGroup.setResult(scalingGroup);

    String lifecycleState = (String) scalingGroup.get("lifecycleState");
    if ("Active".equals(lifecycleState)) {
      serverGroup.setDisabled(false);
    } else {
      serverGroup.setDisabled(true);
    }

    String creationTime = String.valueOf(scalingGroup.get("creationTime"));
    serverGroup.setCreationTime(creationTime);
    try {
      Date d = DateParseHelper.parseUTCTime(creationTime);
      serverGroup.setCreatedTime(d.getTime());
    } catch (ParseException e) {
      e.printStackTrace();
    }
    List<Map> instances = (List<Map>) attributes.get("instances");
    List<String> loadBalancerIds = (List<String>) scalingGroup.get("loadBalancerIds");
    ArrayList<Map> vServerGroups = (ArrayList<Map>) scalingGroup.get("vserverGroups");
    if (vServerGroups != null) {
      for (Map vServerGroup : vServerGroups) {
        String loadBalancerId = vServerGroup.get("loadBalancerId").toString();
        if (!loadBalancerIds.contains(loadBalancerId)) {
          loadBalancerIds.add(loadBalancerId);
        }
      }
    }
    for (Map instance : instances) {
      Object id = instance.get("instanceId");
      if (id != null) {
        String healthStatus = (String) instance.get("healthStatus");
        boolean flag = "Healthy".equals(healthStatus);

        List<Map<String, Object>> health = new ArrayList<>();
        Map<String, Object> m = new HashMap<>();
        m.put("type", provider.getDisplayName());
        m.put("healthClass", "platform");
        HealthState healthState =
            !"Active".equals(lifecycleState)
                ? HealthState.Down
                : !flag
                    ? HealthState.Down
                    : HealthHelper.judgeInstanceHealthyState(
                        allHealthyKeys, loadBalancerIds, id.toString(), cacheView);
        if (healthState.equals(HealthState.Unknown)) {
          m.put("state", HealthState.Down);
        } else {
          m.put("state", healthState);
        }
        health.add(m);
        String zone = (String) instance.get("creationType");
        AliCloudInstance i =
            new AliCloudInstance(
                String.valueOf(id), null, zone,  healthState, health);
        s.add(i);
      }
    }
    serverGroup.setInstances(s);

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

    return serverGroup;
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
    Collection<String> allHealthyKeys = cacheView.getIdentifiers(HEALTH.ns);
    if (serverGroupData == null) {
      return null;
    }
    return bulidServerGroup(allHealthyKeys, serverGroupData);
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
    Collection<AliCloudCluster> clusters = translateClusters(clusterData, false);
    return mapResponse(clusters);
  }

  @Override
  public Map<String, Set<AliCloudCluster>> getClusterSummaries(String application) {
    return getClusterDetails(application);
  }

  @Override
  public Set<AliCloudCluster> getClusters(String application, String account) {
    Map<String, Set<AliCloudCluster>> clusterDetails = getClusterDetails(application);
    return clusterDetails.isEmpty() ? null : clusterDetails.get(account);
  }

  @Override
  public Set<AliCloudCluster> getClusters(
      String application, String account, boolean includeDetails) {
    Map<String, Set<AliCloudCluster>> clusterDetails = getClusterDetails(application);
    return clusterDetails.isEmpty() ? null : clusterDetails.get(account);
  }

  @Override
  public AliCloudCluster getCluster(String application, String account, String name) {
    List<CacheData> clusters = new ArrayList<>();
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account));
    if (cluster != null) {
      clusters.add(cluster);
    }
    return translateClusters(clusters, true).size() > 0
        ? translateClusters(clusters, true).get(0)
        : null;
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
