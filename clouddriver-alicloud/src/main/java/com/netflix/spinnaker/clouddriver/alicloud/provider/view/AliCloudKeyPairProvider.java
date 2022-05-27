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
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys.Namespace;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudKeyPair;
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudKeyPairProvider implements KeyPairProvider<AliCloudKeyPair> {

  private final ObjectMapper objectMapper;

  private final Cache cacheView;

  @Autowired
  public AliCloudKeyPairProvider(ObjectMapper objectMapper, Cache cacheView) {
    this.objectMapper = objectMapper;
    this.cacheView = cacheView;
  }

  @Override
  public Set<AliCloudKeyPair> getAll() {

    Set<AliCloudKeyPair> results = new HashSet<>();

    String key = Keys.getKeyPairKey("*", "*", "*");
    Collection<String> allKeyPairKeys =
        cacheView.filterIdentifiers(Namespace.ALI_CLOUD_KEY_PAIRS.ns, key);
    Collection<CacheData> allData =
        cacheView.getAll(
            Namespace.ALI_CLOUD_KEY_PAIRS.ns, allKeyPairKeys, RelationshipCacheFilter.none());
    for (CacheData allDatum : allData) {
      Map<String, Object> attributes = allDatum.getAttributes();
      AliCloudKeyPair keyPair =
          new AliCloudKeyPair(
              String.valueOf(attributes.get("account")),
              String.valueOf(attributes.get("regionId")),
              String.valueOf(attributes.get("keyPairName")),
              String.valueOf(attributes.get("keyPairFingerPrint")));
      results.add(keyPair);
    }

    return results;
  }
}
