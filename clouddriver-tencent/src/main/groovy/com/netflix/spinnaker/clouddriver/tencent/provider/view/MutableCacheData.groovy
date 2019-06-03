package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.netflix.spinnaker.cats.cache.CacheData

class MutableCacheData implements CacheData {
  final String id
  int ttlSeconds = -1
  Map<String, Object> attributes = [:]
  Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

  MutableCacheData(String id) {
    this.id = id
  }
}
