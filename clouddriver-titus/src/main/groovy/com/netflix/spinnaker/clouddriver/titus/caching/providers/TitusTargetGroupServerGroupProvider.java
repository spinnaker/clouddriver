/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.caching.providers;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonTargetGroup;
import com.netflix.spinnaker.clouddriver.aws.model.TargetGroupServerGroupProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.titus.caching.Keys;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

@Slf4j
@Component
public class TitusTargetGroupServerGroupProvider implements TargetGroupServerGroupProvider {

  private final Cache cacheView;

  @Autowired
  public TitusTargetGroupServerGroupProvider(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @Autowired
  AwsLookupUtil awsLookupUtil;

  @Override
  public Map<String, AmazonTargetGroup> getServerGroups(String applicationName,
                                                        Map<String, AmazonTargetGroup> allTargetGroups,
                                                        Collection<CacheData> targetGroupData) {

    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));
    if (application == null ||
      allTargetGroups.isEmpty() ||
      !application.getRelationships().containsKey(TARGET_GROUPS.ns) ||
      application.getRelationships().get(TARGET_GROUPS.ns).isEmpty()) {
      return allTargetGroups;
    }

    Collection<CacheData> applicationServerGroups = resolveRelationshipData(application, SERVER_GROUPS.ns);
    Set<String> instanceKeys = new HashSet<>();
    for (CacheData serverGroup : applicationServerGroups) {
      Map<String, Collection<String>> relationships = serverGroup.getRelationships();
      if (relationships.containsKey(TARGET_GROUPS.ns) &&
        !relationships.get(TARGET_GROUPS.ns).isEmpty() &&
        relationships.containsKey(INSTANCES.ns) &&
        !relationships.get(INSTANCES.ns).isEmpty()) {
        instanceKeys.addAll(relationships.get(INSTANCES.ns));
      }
    }

    Map<String, Map> instances = cacheView.getAll(INSTANCES.ns, instanceKeys)
      .stream()
      .collect(Collectors.toMap(CacheData::getId, CacheData::getAttributes));

    for (CacheData serverGroup : applicationServerGroups) {
      if (serverGroup.getRelationships().containsKey(TARGET_GROUPS.ns)) {
        for (String targetGroup : serverGroup.getRelationships().get(TARGET_GROUPS.ns)) {
          Map<String, String> targetGroupDetails = com.netflix.spinnaker.clouddriver.aws.data.Keys.parse(targetGroup);

          Set<LoadBalancerInstance> targetGroupInstances = new HashSet<>();
          if (serverGroup.getRelationships().containsKey(INSTANCES.ns)) {
            for (String instanceKey : serverGroup.getRelationships().get(INSTANCES.ns)) {
              Map instanceDetails = instances.get(instanceKey);
              String healthKey = com.netflix.spinnaker.clouddriver.aws.data.Keys.getInstanceHealthKey(
                ((Map) instanceDetails.get("task")).get("containerIp").toString(),
                targetGroupDetails.get("account"),
                targetGroupDetails.get("region"),
                "aws-load-balancer-v2-target-group-instance-health"
              );

              CacheData healthData = cacheView.get(HEALTH.ns, healthKey);
              Map<String, Object> health = getTargetGroupHealth(instanceKey, targetGroupDetails, healthData);

              LoadBalancerInstance instance = new LoadBalancerInstance(
                ((Map) instanceDetails.get("task")).get("id").toString(),
                null,
                health
              );
              targetGroupInstances.add(instance);
            }
          }

          Map attributes = serverGroup.getAttributes();
          Map job = (Map) attributes.get("job");
          LoadBalancerServerGroup loadBalancerServerGroup = new LoadBalancerServerGroup(
            job.get("name").toString(),
            attributes.get("account").toString(),
            attributes.get("region").toString(),
            !(Boolean) job.get("inService"),
            Collections.emptySet(),
            targetGroupInstances
          );

          if (allTargetGroups.containsKey(targetGroup)) {
            allTargetGroups.get(targetGroup).getServerGroups().add(loadBalancerServerGroup);
            allTargetGroups.get(targetGroup).set("instances", targetGroupInstances.stream().map(LoadBalancerInstance::getId).collect(Collectors.toSet()));
          }
        }
      }
    }
    return allTargetGroups;
  }

  Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    return source.getRelationships().get(relationship) != null ? cacheView.getAll(relationship, source.getRelationships().get(relationship)) : Collections.emptyList();
  }

  private Map<String, Object> getTargetGroupHealth(String instanceKey,
                                                   Map<String, String> targetGroupDetails,
                                                   CacheData healthData) {
    try {
      if (healthDataContainsTargetGroups(healthData)) {
        Map targetGroupHealth = getTargetGroupHealthData(targetGroupDetails, healthData);

        if (!targetGroupHealth.isEmpty()) {
          Map<String, Object> health = new HashMap<>();
          health.put("targetGroupName", targetGroupHealth.get("targetGroupName").toString());
          health.put("state", targetGroupHealth.get("state").toString());

          if (targetGroupHealth.containsKey("reasonCode")) {
            health.put("reasonCode", targetGroupHealth.get("reasonCode").toString());
          }

          if (targetGroupHealth.containsKey("description")) {
            health.put("description", targetGroupHealth.get("description").toString());
          }

          return health;
        }
      }
    } catch (Exception e) {
      log.error("failed to load health for " + instanceKey, e);
    }

    return Collections.emptyMap();
  }

  private static boolean healthDataContainsTargetGroups(CacheData healthData) {
    return healthData != null
      && healthData.getAttributes().containsKey("targetGroups")
      && !((ArrayList) healthData.getAttributes().get("targetGroups")).isEmpty();
  }

  private static Map getTargetGroupHealthData(Map<String, String> targetGroupDetails, CacheData healthData) {
    List targetGroups = (List) healthData.getAttributes().get("targetGroups");
    return (Map) targetGroups.stream()
      .filter(tgh ->
        ((Map) tgh).get("targetGroupName").toString().equals(targetGroupDetails.get("targetGroup")
      ))
      .findFirst()
      .orElse(Collections.EMPTY_MAP);
  }
}
