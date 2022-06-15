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

package com.netflix.spinnaker.clouddriver.alicloud.common;

import com.netflix.spinnaker.cats.cache.CacheData;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class CacheDataHelper {
  @Nullable
  public static CacheData merge(CacheData o1, CacheData o2) {
    if (o2 == null) {
      return o1;
    }
    if (o1 == null) {
      return o1;
    }

    if (!o1.getId().equals(o2.getId())) {
      throw new RuntimeException("Different ID cannot merge");
    }

    Map<String, Object> attributes = o1.getAttributes();
    attributes.putAll(o2.getAttributes());
    o2.getRelationships()
        .forEach(
            (k, v) -> {
              if (o1.getRelationships().containsKey(k)) {
                o1.getRelationships().get(k).addAll(v);
              } else {
                o1.getRelationships().put(k, v);
              }
            });
    o1.getRelationships().forEach((k, v) -> v.addAll(o2.getRelationships().get(k)));
    return o1;
  }
}
