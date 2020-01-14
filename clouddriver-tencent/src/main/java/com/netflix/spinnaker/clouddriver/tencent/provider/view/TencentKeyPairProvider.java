package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentKeyPair;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Data
@Slf4j
@Component
public class TencentKeyPairProvider implements KeyPairProvider<TencentKeyPair> {
  @Autowired
  public TencentKeyPairProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentKeyPair> getAll() {
    return cacheView
        .getAll(
            KEY_PAIRS.ns,
            cacheView.filterIdentifiers(KEY_PAIRS.ns, Keys.getKeyPairKey("*", "*", "*")),
            RelationshipCacheFilter.none())
        .stream()
        .map(
            it -> {
              return objectMapper.convertValue(
                  it.getAttributes().get("keyPair"), TencentKeyPair.class);
            })
        .collect(Collectors.toSet());
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
