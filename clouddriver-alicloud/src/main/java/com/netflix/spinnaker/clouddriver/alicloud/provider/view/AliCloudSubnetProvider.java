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

import static com.netflix.spinnaker.clouddriver.alicloud.cache.Keys.Namespace.SUBNETS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudSubnet;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudSubnetProvider implements SubnetProvider<AliCloudSubnet> {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  @Autowired
  public AliCloudSubnetProvider(ObjectMapper objectMapper, Cache cacheView) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
  }

  @Override
  public Set<AliCloudSubnet> getAll() {
    Set<AliCloudSubnet> results = new HashSet<>();
    String globalKey = Keys.getSubnetKey("*", "*", "*");
    Collection<String> allSubnetKeys = cacheView.filterIdentifiers(SUBNETS.ns, globalKey);
    Collection<CacheData> allData =
        cacheView.getAll(SUBNETS.ns, allSubnetKeys, RelationshipCacheFilter.none());
    for (CacheData data : allData) {
      AliCloudSubnet aliCloudSubnet =
          objectMapper.convertValue(data.getAttributes(), AliCloudSubnet.class);
      results.add(aliCloudSubnet);
    }

    return results;
  }

  @Override
  public String getCloudProvider() {
    return AliCloudProvider.ID;
  }
}
