/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import yandex.cloud.api.compute.v1.InstanceServiceOuterClass;

@Component
public class YandexInstanceProvider implements InstanceProvider<YandexCloudInstance, String> {
  private final Cache cacheView;
  private AccountCredentialsProvider accountCredentialsProvider;
  private ObjectMapper objectMapper;

  @Getter private final String cloudProvider = YandexCloudProvider.ID;

  @Autowired
  public YandexInstanceProvider(
      Cache cacheView,
      AccountCredentialsProvider accountCredentialsProvider,
      ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public YandexCloudInstance getInstance(final String account, String region, String name) {
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof YandexCloudCredentials)) {
      return null;
    }
    String pattern =
        Keys.getInstanceKey(account, "*", ((YandexCloudCredentials) credentials).getFolder(), name);
    String ns = Keys.Namespace.INSTANCES.getNs();
    return cacheView.filterIdentifiers(ns, pattern).stream()
        .findFirst()
        .map(key -> cacheView.get(ns, key))
        .map(this::instanceFromCacheData)
        .orElse(null);
  }

  /**
   * Non-interface methods for efficient building of GoogleInstance models during cluster or server
   * group requests.
   */
  List<YandexCloudInstance> getInstances(Collection<String> instanceKeys) {
    return getInstanceCacheData(instanceKeys).stream()
        .map(this::instanceFromCacheData)
        .collect(Collectors.toList());
  }

  Collection<CacheData> getInstanceCacheData(Collection<String> keys) {
    return cacheView.getAll(Keys.Namespace.INSTANCES.getNs(), keys);
  }

  @Override
  public String getConsoleOutput(final String account, final String region, String id) {
    AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(account);

    if (!(accountCredentials instanceof YandexCloudCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: " + account + ":" + region);
    }

    YandexCloudInstance instance = getInstance(account, region, id);

    if (instance != null) {
      return ((YandexCloudCredentials) accountCredentials)
          .instanceService()
          .getSerialPortOutput(
              InstanceServiceOuterClass.GetInstanceSerialPortOutputRequest.newBuilder()
                  .setInstanceId(instance.getId())
                  .build())
          .getContents();
    }

    return null;
  }

  public YandexCloudInstance instanceFromCacheData(CacheData cacheData) {
    return objectMapper.convertValue(cacheData.getAttributes(), YandexCloudInstance.class);
  }
}
