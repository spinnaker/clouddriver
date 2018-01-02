/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsTargetGroup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;

@Component
public class EcsTargetGroupCacheClient {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  public EcsTargetGroupCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  public List<EcsTargetGroup> findAll() {
    String searchKey = Keys.getTargetGroupKey("*", "*", "*", "*") + "*";
    Collection<String> targetGroupKeys = cacheView.filterIdentifiers(TARGET_GROUPS.getNs(), searchKey);

    Set<Map<String, Object>> targetGroupAttributes = fetchLoadBalancerAttributes(targetGroupKeys);

    List<EcsTargetGroup> targetGroups = convertToTargetGroup(targetGroupAttributes);

    return targetGroups;
  }

  private EcsTargetGroup convertToTargetGroup(Map<String, Object> targetGroupAttributes) {
    targetGroupAttributes.toString();
    EcsTargetGroup ecsTargetGroup = objectMapper.convertValue(targetGroupAttributes, EcsTargetGroup.class);
    return ecsTargetGroup;
  }

  private List<EcsTargetGroup> convertToTargetGroup(Collection<Map<String, Object>> targetGroupAttributes) {
    List<EcsTargetGroup> ecsTargetGroups = new ArrayList<>();

    for (Map<String, Object> attributes : targetGroupAttributes) {
      ecsTargetGroups.add(convertToTargetGroup(attributes));
    }

    return ecsTargetGroups;
  }


  private Set<Map<String, Object>> fetchLoadBalancerAttributes(Collection<String> targetGroupKeys) {
    Set<CacheData> targetGroups = fetchTargetGroups(targetGroupKeys);

    Set<CacheData> targetGroupsWithAtLeastOneLoadBalancer = targetGroups
      .stream()
      .filter(targetGroup -> targetGroup.getRelationships().get("loadBalancers") != null
        && targetGroup.getRelationships().get("loadBalancers").size() > 0)
      .collect(Collectors.toSet());

    Set<Map<String, Object>> targetGroupAttributes = targetGroupsWithAtLeastOneLoadBalancer.stream()
      .map(lb -> lb.getAttributes())
      .collect(Collectors.toSet());

    return targetGroupAttributes;
  }


  private Set<Map<String, Object>> retrieveTargetGroups(Set<String> targetGroupsAssociatedWithLoadBalancers) {
    Collection<CacheData> targetGroupCache = cacheView.getAll(TARGET_GROUPS.getNs(), targetGroupsAssociatedWithLoadBalancers);
    Set<Map<String, Object>> targetGroupAttributes = targetGroupCache
      .stream()
      .map(lb -> lb.getAttributes())
      .collect(Collectors.toSet());

    return targetGroupAttributes;
  }


  private Set<String> inferAssociatedTargetGroups(Set<CacheData> loadBalancers) {
    Set<String> targetGroupsAssociatedWithLoadBalancers = new HashSet<>();

    for (CacheData loadBalancer : loadBalancers) {
      Collection<String> relatedTargetGroups = loadBalancer.getRelationships().get("targetGroups");
      if (relatedTargetGroups != null && relatedTargetGroups.size() > 0) {
        for (String targetGroupArn : relatedTargetGroups) {
          targetGroupsAssociatedWithLoadBalancers.add(targetGroupArn);
        }
      }
    }
    return targetGroupsAssociatedWithLoadBalancers;
  }

  private Set<CacheData> fetchTargetGroups(Collection<String> targetGroupKeys) {
    return cacheView
      .getAll(TARGET_GROUPS.getNs(), targetGroupKeys, RelationshipCacheFilter.include(LOAD_BALANCERS.getNs()))
      .stream()
      .collect(Collectors.toSet());
  }


  private String extractAccount(EcsTargetGroup ecsTargetGroup) {
    return null; // Implement this based on ecsTargetGroup.getId(); -->  example value: aws:targetGroups:continuous-delivery:us-west-2:clouddriver-dev:vpc-bb8f37dd
  }

  private String extractRegion(EcsTargetGroup ecsTargetGroup) {
    return null; // Implement this based on ecsTargetGroup.getId(); -->  example value: aws:targetGroups:continuous-delivery:us-west-2:clouddriver-dev:vpc-bb8f37dd
  }
}
