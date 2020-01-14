package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstanceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.INSTANCE_TYPES;

@Component
public class TencentInstanceTypeProvider implements InstanceTypeProvider<TencentInstanceType> {
  @Autowired
  public TencentInstanceTypeProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentInstanceType> getAll() {
    return cacheView.getAll(INSTANCE_TYPES.ns,
      cacheView.filterIdentifiers(INSTANCE_TYPES.ns,
        Keys.getInstanceTypeKey("*", "*", "*")),
      RelationshipCacheFilter.none()).stream().map(it -> {
      return objectMapper.convertValue(it.getAttributes().get("instanceType"), TencentInstanceType.class);
    })
      .sorted(Comparator.comparing(TencentInstanceType::getInstanceFamily)
        .thenComparing(TencentInstanceType::getCpu)
        .thenComparing(TencentInstanceType::getMem))
      .collect(Collectors.toSet());
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @Autowired
  private Cache cacheView;
  private final ObjectMapper objectMapper;
}
