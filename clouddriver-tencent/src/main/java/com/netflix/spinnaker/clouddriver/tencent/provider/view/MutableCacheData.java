package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import com.netflix.spinnaker.cats.cache.CacheData;
import lombok.Data;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Data
public class MutableCacheData implements CacheData {
  public MutableCacheData(String id) {
    this.id = id;
  }

  public final String getId() {
    return id;
  }

  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public Map<String, Collection<String>> getRelationships() {
    return relationships;
  }

  private final String id;
  private int ttlSeconds = -1;
  private Map<String, Object> attributes = new HashMap<String, Object>();
  private Map<String, Collection<String>> relationships = new Relationships();

  public static class Relationships extends HashMap<String, Collection<String>> {
    @Override
    public Collection<String> get(Object key) {
      super.putIfAbsent((String) key, new HashSet<String>());
      return super.get(key);
    }
  }
}
