package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentKeyPair;
import groovy.lang.Closure;
import groovy.util.logging.Slf4j;
import java.util.Set;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TencentKeyPairProvider implements KeyPairProvider<TencentKeyPair> {
  @Autowired
  public TencentKeyPairProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentKeyPair> getAll() {
    return DefaultGroovyMethods.collect(
        cacheView.getAll(
            KEY_PAIRS.ns,
            cacheView.filterIdentifiers(KEY_PAIRS.ns, Keys.getKeyPairKey("*", "*", "*")),
            RelationshipCacheFilter.none()),
        new Closure<TencentKeyPair>(this, this) {
          public TencentKeyPair doCall(CacheData it) {
            return objectMapper.convertValue(it.getAttributes().keyPair, TencentKeyPair.class);
          }

          public TencentKeyPair doCall() {
            return doCall(null);
          }
        });
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @Autowired private Cache cacheView;
  private final ObjectMapper objectMapper;
}
