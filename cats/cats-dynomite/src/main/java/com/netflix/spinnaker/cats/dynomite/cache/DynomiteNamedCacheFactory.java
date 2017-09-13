/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cats.dynomite.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteCache.CacheMetrics;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;

public class DynomiteNamedCacheFactory implements NamedCacheFactory {

  private final DynomiteClientDelegate dynomiteClientDelegate;
  private final ObjectMapper objectMapper;
  private final RedisCacheOptions options;
  private final CacheMetrics cacheMetrics;

  public DynomiteNamedCacheFactory(DynomiteClientDelegate dynomiteClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    this.dynomiteClientDelegate = dynomiteClientDelegate;
    this.objectMapper = objectMapper;
    this.options = options;
    this.cacheMetrics = cacheMetrics;
  }

  @Override
  public WriteableCache getCache(String name) {
    return new DynomiteCache(name, dynomiteClientDelegate, objectMapper, options, cacheMetrics);
  }
}
