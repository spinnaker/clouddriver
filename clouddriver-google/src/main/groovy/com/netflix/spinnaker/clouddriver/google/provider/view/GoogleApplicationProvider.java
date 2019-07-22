/*
 * Copyright 2019 Google, LLC
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
 *
 */

package com.netflix.spinnaker.clouddriver.google.provider.view;

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.INSTANCES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace;
import com.netflix.spinnaker.clouddriver.google.model.GoogleApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class GoogleApplicationProvider implements ApplicationProvider {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  GoogleApplicationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<? extends Application> getApplications(boolean expand) {

    RelationshipCacheFilter filter =
        expand ? RelationshipCacheFilter.include(CLUSTERS.getNs()) : RelationshipCacheFilter.none();
    Collection<CacheData> data =
        cacheView.getAll(
            APPLICATIONS.getNs(),
            cacheView.filterIdentifiers(APPLICATIONS.getNs(), GoogleCloudProvider.getID() + ":*"),
            filter);
    return data.stream().map(this::applicationFromCacheData).collect(toSet());
  }

  @Override
  public Application getApplication(String name) {

    CacheData cacheData =
        cacheView.get(
            APPLICATIONS.getNs(),
            Keys.getApplicationKey(name),
            RelationshipCacheFilter.include(CLUSTERS.getNs(), INSTANCES.getNs()));
    if (cacheData == null) {
      return null;
    }

    return applicationFromCacheData(cacheData);
  }

  private GoogleApplication.View applicationFromCacheData(CacheData cacheData) {

    GoogleApplication application =
        objectMapper.convertValue(cacheData.getAttributes(), GoogleApplication.class);
    if (application == null) {
      return null;
    }

    GoogleApplication.View applicationView = application.getView();

    Collection<String> clusters = getRelationships(cacheData, CLUSTERS);
    clusters.forEach(
        key -> {
          Map<String, String> parsedKey = Keys.parse(key);
          applicationView
              .getClusterNames()
              .get(parsedKey.get("account"))
              .add(parsedKey.get("name"));
        });

    List<Map<String, String>> instances =
        getRelationships(cacheData, INSTANCES).stream().map(Keys::parse).collect(toList());
    applicationView.setInstances(instances);

    return applicationView;
  }

  private Collection<String> getRelationships(CacheData cacheData, Namespace namespace) {
    Collection<String> result = cacheData.getRelationships().get(namespace.getNs());
    return result != null ? result : ImmutableSet.of();
  }
}
