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

package com.netflix.spinnaker.clouddriver.alicloud.common;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.model.alienum.LifecycleState;
import com.netflix.spinnaker.clouddriver.alicloud.model.alienum.ScalingInstanceHealthStatus;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

public class HealthHelper {

  public static boolean healthyStateMatcher(
      String key, Set<String> loadBalancerId, String instanceId) {

    if (StringUtils.isBlank(key)) {
      return false;
    }

    Map<String, String> details = Keys.parse(key);
    if (details == null || details.isEmpty()) {
      return false;
    }

    boolean instanceMatch = StringUtils.equals(instanceId, details.get("instanceId"));
    if (loadBalancerId == null || loadBalancerId.isEmpty()) {
      return instanceMatch;
    }
    return loadBalancerId.contains(details.get("loadBalancerId"));
  }

  private static HealthState judgeInstanceHealthyState(Collection<CacheData> healthData) {
    if (CollectionUtils.isEmpty(healthData)) {
      return HealthState.Unknown;
    }
    Map<String, Integer> healthMap = new HashMap<>(16);
    for (CacheData cacheData : healthData) {
      String serverHealthStatus = cacheData.getAttributes().get("serverHealthStatus").toString();
      healthMap.put(serverHealthStatus, healthMap.getOrDefault(serverHealthStatus, 0) + 1);
    }
    Integer normal = healthMap.get("normal");
    Integer abnormal = healthMap.get("abnormal");
    if (normal != null && normal > 0 && abnormal == null) {
      return HealthState.Up;
    } else if (abnormal != null && abnormal > 0 && normal == null) {
      return HealthState.Down;
    } else if (abnormal == null && normal == null) {
      return HealthState.Down;
    } else {
      return HealthState.Unknown;
    }
  }

  public static ScalingInstanceHealthStatus loadBalancerInstanceHealthState(
      String scalingGroupLifecycleState,
      String scalingInstanceInstanceHealthStatus,
      Collection<CacheData> healthData) {
    if (!LifecycleState.Active.name().equals(scalingGroupLifecycleState)) {
      return ScalingInstanceHealthStatus.Unhealthy;
    }

    if (!ScalingInstanceHealthStatus.Healthy.name().equals(scalingInstanceInstanceHealthStatus)) {
      return ScalingInstanceHealthStatus.Unhealthy;
    }
    HealthState healthState = judgeInstanceHealthyState(healthData);
    return ScalingInstanceHealthStatus.forState(healthState);
  }

  public static HealthState genInstanceHealthState(
      String instanceStatus, Collection<CacheData> healthData) {
    return "Running".equals(instanceStatus)
        ? judgeInstanceHealthyState(healthData)
        : HealthState.Down;
  }

  public static HealthState genInstanceHealthState(
      String scalingGroupLifecycleState, String instanceStatus, Collection<CacheData> healthData) {
    if (!LifecycleState.Active.name().equals(scalingGroupLifecycleState)) {
      return HealthState.Down;
    }
    if (!"Running".equals(instanceStatus)) {
      return HealthState.Down;
    }
    return HealthHelper.judgeInstanceHealthyState(healthData);
  }
}
