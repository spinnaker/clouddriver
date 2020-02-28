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

import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace;
import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.getApplicationKey;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexApplication;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class YandexApplicationProvider implements ApplicationProvider {
  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  YandexApplicationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<YandexApplication> getApplications(boolean expand) {
    String applicationsNs = Namespace.APPLICATIONS.getNs();
    Collection<String> identifiers =
        cacheView.filterIdentifiers(applicationsNs, getApplicationKey("*"));
    RelationshipCacheFilter cacheFilter =
        expand
            ? RelationshipCacheFilter.include(
                Namespace.CLUSTERS.getNs(), Namespace.INSTANCES.getNs())
            : RelationshipCacheFilter.none();
    return cacheView.getAll(applicationsNs, identifiers, cacheFilter).stream()
        .map(this::applicationFromCacheData)
        .collect(toSet());
  }

  @Override
  public YandexApplication getApplication(String name) {
    CacheData cacheData =
        cacheView.get(
            Namespace.APPLICATIONS.getNs(),
            getApplicationKey(name),
            RelationshipCacheFilter.include(
                Namespace.CLUSTERS.getNs(), Namespace.INSTANCES.getNs()));
    return applicationFromCacheData(cacheData);
  }

  private YandexApplication applicationFromCacheData(CacheData cacheData) {
    if (cacheData == null) {
      return null;
    }
    Map<String, Object> attributes = cacheData.getAttributes();
    YandexApplication application = objectMapper.convertValue(attributes, YandexApplication.class);
    if (application == null) {
      return null;
    }

    getRelationships(cacheData, Namespace.CLUSTERS).stream()
        .map(Keys::parse)
        .filter(Objects::nonNull)
        .forEach(
            parts ->
                application
                    .getClusterNames()
                    .computeIfAbsent(parts.get("account"), s -> new HashSet<>())
                    .add(parts.get("name")));

    List<Map<String, String>> instances =
        getRelationships(cacheData, Namespace.INSTANCES).stream()
            .map(Keys::parse)
            .collect(toList());
    application.setInstances(instances);

    return application;
  }

  private Set<String> getRelationships(CacheData cacheData, Namespace namespace) {
    Collection<String> relationships = cacheData.getRelationships().get(namespace.getNs());
    return relationships == null ? Collections.emptySet() : new HashSet<>(relationships);
  }
}
