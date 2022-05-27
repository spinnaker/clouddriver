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

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.common.HealthHelper;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudInstance;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudInstanceProvider implements InstanceProvider<AliCloudInstance, String> {

  private final ObjectMapper objectMapper;
  private final Cache cacheView;
  private final AliCloudProvider provider;

  @Autowired
  public AliCloudInstanceProvider(
      ObjectMapper objectMapper, Cache cacheView, AliCloudProvider provider) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
    this.provider = provider;
  }

  @Override
  public AliCloudInstance getInstance(String account, String region, String id) {

    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region));
    if (instanceEntry == null) {
      return null;
    }

    Map<String, Object> attributes = instanceEntry.getAttributes();
    String instanceId = (String) attributes.get("instanceId");
    String zone = (String) attributes.get("zoneId");

    if (instanceId != null) {
      Collection<String> cacheKeys =
          cacheView.filterIdentifiers(
              HEALTH.ns, Keys.getInstanceHealthKey("*", id, "*", account, region, "*"));
      Collection<CacheData> healthData =
          cacheView.getAll(HEALTH.ns, cacheKeys.toArray(new String[] {}));

      HealthState healthState =
          HealthHelper.genInstanceHealthState(String.valueOf(attributes.get("status")), healthData);

      List<Map<String, Object>> health = new ArrayList<>();
      Map<String, Object> m = new HashMap<>();
      m.put("type", provider.getDisplayName());
      m.put("healthClass", "platform");
      m.put("state", healthState);
      health.add(m);

      AliCloudInstance instance =
          new AliCloudInstance(String.valueOf(id), null, zone, healthState, health);
      instance.setAttributes(attributes);

      return instance;
    }
    return null;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region));
    if (instanceEntry == null) {
      return null;
    }

    Map<String, Object> attributes = instanceEntry.getAttributes();
    return String.valueOf(attributes.getOrDefault("instanceName", ""));
  }

  @Override
  public String getCloudProvider() {
    return AliCloudProvider.ID;
  }
}
