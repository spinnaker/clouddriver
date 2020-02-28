/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.model.SubnetProvider;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudSubnet;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class YandexSubnetProvider implements SubnetProvider<YandexCloudSubnet> {
  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Getter private final String cloudProvider = YandexCloudProvider.ID;

  @Autowired
  public YandexSubnetProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<YandexCloudSubnet> getAll() {
    return loadResults(
        cacheView.filterIdentifiers(
            Keys.Namespace.SUBNETS.getNs(), Keys.getSubnetKey("*", "*", "*", "*")));
  }

  private Set<YandexCloudSubnet> loadResults(Collection<String> identifiers) {
    return cacheView.getAll(Keys.Namespace.SUBNETS.getNs(), identifiers).stream()
        .map(this::fromCacheData)
        .collect(Collectors.toSet());
  }

  private YandexCloudSubnet fromCacheData(CacheData cacheData) {
    return objectMapper.convertValue(cacheData.getAttributes(), YandexCloudSubnet.class);
  }
}
