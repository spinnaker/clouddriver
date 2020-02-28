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

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.yandex.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.provider.YandexInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public abstract class AbstractYandexCachingAgent implements CachingAgent, AccountAware {
  static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  private final String providerName = YandexInfrastructureProvider.class.getName();
  private YandexCloudCredentials credentials;
  private ObjectMapper objectMapper;

  AbstractYandexCachingAgent(YandexCloudCredentials credentials, ObjectMapper objectMapper) {
    this.credentials = credentials;
    this.objectMapper = objectMapper;
  }

  String getFolder() {
    return credentials == null ? null : credentials.getFolder();
  }

  public String getAccountName() {
    return credentials == null ? null : credentials.getName();
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder, String key)
      throws IOException {
    Map<String, List<DefaultCacheData>> onDemandData =
        getObjectMapper()
            .readValue(
                (String)
                    cacheResultBuilder
                        .getOnDemand()
                        .getToKeep()
                        .get(key)
                        .getAttributes()
                        .get("cacheResults"),
                new TypeReference<Map<String, List<DefaultCacheData>>>() {});
    onDemandData.forEach(
        (namespace, cacheDatas) -> {
          if (namespace.equals(Keys.Namespace.ON_DEMAND.getNs())) {
            return;
          }

          cacheDatas.forEach(
              cacheData -> {
                CacheResultBuilder.CacheDataBuilder keep =
                    cacheResultBuilder.namespace(namespace).keep(cacheData.getId());
                keep.setAttributes(cacheData.getAttributes());
                keep.setRelationships(
                    mergeOnDemandCacheRelationships(
                        cacheData.getRelationships(), keep.getRelationships()));
                cacheResultBuilder.getOnDemand().getToKeep().remove(cacheData.getId());
              });
        });
  }

  private static Map<String, Collection<String>> mergeOnDemandCacheRelationships(
      Map<String, Collection<String>> onDemandRelationships,
      Map<String, Collection<String>> existingRelationships) {
    return Stream.concat(
            existingRelationships.keySet().stream(), onDemandRelationships.keySet().stream())
        .distinct()
        .collect(
            toMap(
                Function.identity(),
                key ->
                    Stream.of(existingRelationships.get(key), onDemandRelationships.get(key))
                        .filter(Objects::nonNull)
                        .flatMap(Collection::stream)
                        .filter(Objects::nonNull)
                        .collect(toList())));
  }
}
