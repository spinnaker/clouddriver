/*
 * Copyright 2022 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AliCloudApplicationProvider implements ApplicationProvider {
  @Autowired private Cache cacheView;
  @Autowired private AliCloudProvider provider;

  @Override
  public Set<? extends Application> getApplications(boolean expand) {
    Collection<CacheData> cacheDatas =
        cacheView.getAll(APPLICATIONS.ns, Keys.getApplicationKey("*"));
    if (cacheDatas == null) {
      return null;
    }

    return cacheDatas.stream()
        .filter(Objects::nonNull)
        .map(
            cacheData -> {
              String name = String.valueOf(cacheData.getAttributes().get("name"));
              return buildAliCloudApplication(name, cacheData, expand);
            })
        .collect(Collectors.toSet());
  }

  @Nullable
  @Override
  public Application getApplication(String name) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(name));
    if (application == null) {
      return null;
    }
    return buildAliCloudApplication(name, application, true);
  }

  @NotNull
  private AliCloudApplication buildAliCloudApplication(
      String name, CacheData application, boolean expand) {
    Map<String, Set<String>> clusterNames = new HashMap<>();
    if (!expand) {
      return new AliCloudApplication(name, clusterNames, Map.of("name", name));
    }

    Collection<String> clusterKeys = application.getRelationships().get(CLUSTERS.ns);
    for (String key : clusterKeys) {
      Map<String, String> clusterinfo = Keys.parse(key);
      if (clusterinfo == null) {
        continue;
      }
      clusterNames.compute(
          clusterinfo.get("account"),
          (k, v) -> {
            if (v == null) {
              v = new HashSet<>();
            }
            v.add(clusterinfo.get("clusterName"));
            return v;
          });
    }

    return new AliCloudApplication(name, clusterNames, Map.of("name", name));
  }
}
