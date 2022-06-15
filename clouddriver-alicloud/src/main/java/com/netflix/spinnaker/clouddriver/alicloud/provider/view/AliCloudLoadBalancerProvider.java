/*
 * Copyright 2019 Alibaba Group.
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
 */
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.common.HealthHelper;
import com.netflix.spinnaker.clouddriver.alicloud.common.Sets;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudLoadBalancerProvider implements LoadBalancerProvider<AliCloudLoadBalancer> {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  private final AliCloudProvider provider;

  @Autowired
  public AliCloudLoadBalancerProvider(
      ObjectMapper objectMapper, Cache cacheView, AliCloudProvider provider) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
    this.provider = provider;
  }

  @Override
  public Set<AliCloudLoadBalancer> getApplicationLoadBalancers(String applicationName) {

    Collection<CacheData> applicationServerGroups =
        getServerGroupCacheDataByApplication(applicationName);
    Collection<String> allLoadBalancerKeys = cacheView.getIdentifiers(LOAD_BALANCERS.ns);
    Collection<String> loadBalancerKeyMatches =
        allLoadBalancerKeys.stream()
            .filter(tab -> applicationMatcher(tab, applicationName))
            .collect(Collectors.toList());
    Collection<CacheData> loadBalancerData =
        cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeyMatches);

    Set<AliCloudLoadBalancer> loadBalances = new HashSet<>();
    for (CacheData cacheData : loadBalancerData) {
      Map<String, Object> attributes =
          objectMapper.convertValue(cacheData.getAttributes(), Map.class);
      String id = cacheData.getId();
      AliCloudLoadBalancer loadBalancer =
          new AliCloudLoadBalancer(
              String.valueOf(attributes.get("account")),
              String.valueOf(attributes.get("regionIdAlias")),
              String.valueOf(attributes.get("loadBalancerName")),
              String.valueOf(attributes.get("vpcId")),
              String.valueOf(attributes.get("loadBalancerId")));
      for (CacheData applicationServerGroup : applicationServerGroups) {
        Collection<String> loadBalancers =
            applicationServerGroup.getRelationships().get("loadBalancers");
        for (String balancer : loadBalancers) {
          if (id.startsWith(balancer)) {
            addServerGroupToLoadBalancer(loadBalancer, applicationServerGroup);

            Map<String, Object> map =
                (Map<String, Object>)
                    applicationServerGroup.getAttributes().get("scalingConfiguration");
            if (map.containsKey("securityGroupIds")) {
              Set<String> securityGroups = new HashSet<>();
              securityGroups.addAll((Collection<String>) map.get("securityGroupIds"));
              loadBalancer.setSecurityGroups(securityGroups);
            }

            break;
          }
        }
      }
      loadBalances.add(loadBalancer);
    }
    return loadBalances;
  }

  @Override
  public List<ResultDetails> byAccountAndRegionAndName(String account, String region, String name) {
    List<ResultDetails> results = new ArrayList<>();
    String searchKey = Keys.getLoadBalancerKey(name, account, region, null) + "*";
    Collection<String> allLoadBalancerKeys =
        cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey);
    Collection<CacheData> loadBalancers =
        cacheView.getAll(LOAD_BALANCERS.ns, allLoadBalancerKeys, null);
    for (CacheData loadBalancer : loadBalancers) {
      ResultDetails resultDetails = new ResultDetails();
      Set<LoadBalancerServerGroup> serverGroups = new HashSet<>();
      String id = loadBalancer.getId();
      String applicationName = getApplicationByName(name);
      Collection<CacheData> applicationServerGroups =
          getServerGroupCacheDataByApplication(applicationName);
      for (CacheData applicationServerGroup : applicationServerGroups) {
        Collection<String> relationships =
            applicationServerGroup.getRelationships().get("loadBalancers");
        for (String loadBalancerId : relationships) {
          if (id.startsWith(loadBalancerId)) {
            LoadBalancerServerGroup loadBalancerServerGroup =
                createLoadBalancerServerGroup(loadBalancerId, applicationServerGroup);
            serverGroups.add(loadBalancerServerGroup);
            break;
          }
        }
      }
      Map<String, Object> attributes = loadBalancer.getAttributes();
      attributes.put("serverGroups", serverGroups);
      resultDetails.setResults(attributes);
      results.add(resultDetails);
    }
    return results;
  }

  @Override
  public String getCloudProvider() {
    return AliCloudProvider.ID;
  }

  @Override
  public List<? extends Item> list() {
    return null;
  }

  @Override
  public Item get(String name) {
    return null;
  }

  private static boolean applicationMatcher(String key, String applicationName) {
    String regex1 = AliCloudProvider.ID + ":.*:" + applicationName + "-.*";
    String regex2 = AliCloudProvider.ID + ":.*:" + applicationName;
    String regex3 = AliCloudProvider.ID + ":.*:" + applicationName + ":.*";
    return Pattern.matches(regex1, key)
        || Pattern.matches(regex2, key)
        || Pattern.matches(regex3, key);
  }

  private LoadBalancerServerGroup createLoadBalancerServerGroup(
      String loadBalancerId, CacheData applicationServerGroup) {
    LoadBalancerServerGroup loadBalancerServerGroup = new LoadBalancerServerGroup();
    Map<String, Object> attributes = applicationServerGroup.getAttributes();
    loadBalancerServerGroup.setName(String.valueOf(attributes.get("name")));
    loadBalancerServerGroup.setCloudProvider(AliCloudProvider.ID);
    loadBalancerServerGroup.setRegion(String.valueOf(attributes.get("region")));
    loadBalancerServerGroup.setAccount(String.valueOf(attributes.get("account")));
    Map<String, Object> scalingGroup = (Map) attributes.get("scalingGroup");
    String scalingGroupLifecycleState = (String) scalingGroup.get("lifecycleState");
    loadBalancerServerGroup.setIsDisabled(!"Active".equals(scalingGroupLifecycleState));

    List<Map> instances = (List<Map>) attributes.get("instances");
    if (instances != null) {
      Collection<String> allHealthyKeys = cacheView.getIdentifiers(HEALTH.ns);

      loadBalancerServerGroup.setInstances(
          instances.stream()
              .filter(instance -> instance.get("instanceId") != null)
              .map(
                  instance -> {
                    LoadBalancerInstance loadBalancerInstance = new LoadBalancerInstance();
                    String instanceId = String.valueOf(instance.get("instanceId"));
                    loadBalancerInstance.setId(instanceId);
                    loadBalancerInstance.setName(instanceId);
                    loadBalancerInstance.setZone(String.valueOf(instance.get("creationType")));

                    List<String> healthKeys =
                        allHealthyKeys.stream()
                            .filter(
                                k ->
                                    HealthHelper.healthyStateMatcher(
                                        k, Sets.ofModifiable(loadBalancerId), instanceId))
                            .collect(Collectors.toList());
                    Collection<CacheData> healthDatas =
                        cacheView.getAll(HEALTH.ns, healthKeys.toArray(new String[] {}));

                    Map<String, Object> health = new HashMap<>();
                    health.put("type", provider.getDisplayName());
                    health.put("healthClass", "platform");
                    health.put(
                        "state",
                        HealthHelper.loadBalancerInstanceHealthState(
                            scalingGroupLifecycleState,
                            String.valueOf(instance.get("healthStatus")),
                            healthDatas));
                    loadBalancerInstance.setHealth(health);
                    return loadBalancerInstance;
                  })
              .collect(Collectors.toSet()));
    }

    return loadBalancerServerGroup;
  }

  public void addServerGroupToLoadBalancer(
      AliCloudLoadBalancer loadBalancer, CacheData applicationServerGroup) {
    if (!isContainsLoadBalancer(loadBalancer, applicationServerGroup)) {
      return;
    }
    Set<LoadBalancerServerGroup> serverGroups = loadBalancer.getServerGroups();
    if (serverGroups == null) {
      serverGroups = new HashSet<>();
    }
    LoadBalancerServerGroup serverGroup =
        createLoadBalancerServerGroup(loadBalancer.getLoadBalancerId(), applicationServerGroup);
    serverGroups.add(serverGroup);
    loadBalancer.setServerGroups(serverGroups);
  }

  private boolean isContainsLoadBalancer(
      AliCloudLoadBalancer loadBalancer, CacheData applicationServerGroup) {
    String loadBalancerKey =
        Keys.getLoadBalancerKey(
            loadBalancer.getName(),
            loadBalancer.getAccount(),
            loadBalancer.getRegion(),
            loadBalancer.getVpcId());
    return applicationServerGroup
        .getRelationships()
        .get(LOAD_BALANCERS.ns)
        .contains(loadBalancerKey);
  }

  class ResultDetails implements Details {
    Map results;

    public Map getResults() {
      return results;
    }

    public void setResults(Map results) {
      this.results = results;
    }
  }

  private String getApplicationByName(String name) {
    AliCloudLoadBalancer loadBalancer = new AliCloudLoadBalancer(null, null, name, null, null);
    return loadBalancer.getMoniker().getApp();
  }

  private Collection<CacheData> getServerGroupCacheDataByApplication(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));
    if (application == null) {
      return Collections.emptyList();
    }
    Map<String, Collection<String>> relationships = application.getRelationships();
    Collection<String> keys = relationships.get(SERVER_GROUPS.ns);

    return cacheView.getAll(SERVER_GROUPS.ns, keys);
  }
}
