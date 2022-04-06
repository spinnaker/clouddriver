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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.view.AliCloudInstanceTypeProvider.Zone;
import com.netflix.spinnaker.clouddriver.model.InstanceType;
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudInstanceTypeProvider implements InstanceTypeProvider<Zone> {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  @Autowired
  public AliCloudInstanceTypeProvider(ObjectMapper objectMapper, Cache cacheView) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
  }

  @Override
  public Set<Zone> getAll() {
    Set<Zone> results = new HashSet<>();
    String globalKey = Keys.getInstanceTypeKey("*", "*", "*");
    Collection<String> allInstanceTypeKeys =
        cacheView.filterIdentifiers(Keys.Namespace.INSTANCE_TYPES.ns, globalKey);
    Collection<CacheData> allData =
        cacheView.getAll(
            Keys.Namespace.INSTANCE_TYPES.ns, allInstanceTypeKeys, RelationshipCacheFilter.none());
    for (CacheData allDatum : allData) {
      Map<String, Object> attributes = allDatum.getAttributes();

      Zone zone =
          new Zone(
              String.valueOf(attributes.get("account")),
              String.valueOf(attributes.get("regionId")),
              String.valueOf(attributes.get("zoneId")),
              String.valueOf(attributes.get("provider")),
              objectMapper.convertValue(attributes.get("names"), ArrayList.class));
      results.add(zone);
    }
    return results;
  }

  class Zone implements InstanceType {
    String account;
    String regionId;
    String zoneId;
    String provider;
    List<String> instanceTypes;

    public Zone(
        String account,
        String regionId,
        String zoneId,
        String provider,
        List<String> instanceTypes) {
      this.account = account;
      this.regionId = regionId;
      this.zoneId = zoneId;
      this.provider = provider;
      this.instanceTypes = instanceTypes;
    }

    @Override
    public String getName() {
      return account;
    }

    public String getAccount() {
      return account;
    }

    public String getRegionId() {
      return regionId;
    }

    public String getZoneId() {
      return zoneId;
    }

    public List<String> getInstanceTypes() {
      return instanceTypes;
    }

    public String getProvider() {
      return provider;
    }
  }
}
