package com.netflix.spinnaker.clouddriver.tencent.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.model.TencentKeyPair
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.KEY_PAIRS

@Slf4j
@Component
class TencentKeyPairProvider implements KeyPairProvider<TencentKeyPair> {

  @Autowired
  Cache cacheView

  private final ObjectMapper objectMapper

  @Autowired
  TencentKeyPairProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper
  }

  @Override
  Set<TencentKeyPair> getAll() {
    cacheView.getAll(
      KEY_PAIRS.ns,
      cacheView.filterIdentifiers(
        KEY_PAIRS.ns,
        Keys.getKeyPairKey('*','*','*')
      ),
      RelationshipCacheFilter.none()).collect {
      objectMapper.convertValue it.attributes.keyPair, TencentKeyPair
    }
  }
}
