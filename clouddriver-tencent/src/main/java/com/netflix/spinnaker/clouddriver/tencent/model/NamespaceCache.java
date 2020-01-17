/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData;
import java.util.HashMap;

public class NamespaceCache extends HashMap<String, NamespaceCache.Cache> {
  @Override
  public Cache get(Object key) {
    super.putIfAbsent((String) key, new Cache());
    return super.get(key);
  }

  public static class Cache extends HashMap<String, CacheData> {
    @Override
    public CacheData get(Object key) {
      super.putIfAbsent((String) key, new MutableCacheData((String) key));
      return super.get(key);
    }
  }
}
