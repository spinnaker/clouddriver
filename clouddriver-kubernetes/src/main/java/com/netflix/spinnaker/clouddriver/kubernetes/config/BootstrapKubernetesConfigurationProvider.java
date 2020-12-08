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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.unflattener.JsonUnflattener;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileLoadingException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * For larger number of Kubernetes accounts, SpringBoot implementation of properties binding is
 * inefficient, hence a custom constructor for KubernetesConfigurationProperties is written. This
 * class fetches the flattened kubernetes properties from Spring Cloud Config's
 * BootstrapPropertySource and creates a KubernetesConfigurationProperties object. Also adds support
 * to existing "configserver:" feature along with caching ability. The main objective of this
 * implementation is to reduce the clouddriver loading time when large number of dynamic accounts
 * are configured. So some of the features provided by SpringBoot and clouddriver modules do not
 * work. Here are the limitations: 1. The properties in the yaml file must use camel case 2.
 * Property placeholders don't work. 3. "encryptedFile:" and "encrypted:" notations don't work 4.
 * "configserver:" works only for kubeconfig files
 */
public class BootstrapKubernetesConfigurationProvider implements KubernetesConfigurationProvider {

  private final ConfigurableApplicationContext applicationContext;
  private CloudConfigResourceService configResourceService;
  private Map<String, String> configServerCache;

  public BootstrapKubernetesConfigurationProvider(
      ConfigurableApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public KubernetesConfigurationProperties getKubernetesConfigurationProperties() {
    return getKubernetesConfigurationProperties(getPropertiesMap());
  }

  @SuppressWarnings("unchecked")
  public KubernetesConfigurationProperties getKubernetesConfigurationProperties(
      Map<String, Object> kubernetesPropertiesMap) {
    ObjectMapper objectMapper = new ObjectMapper();
    // remove the keys having blank string values
    kubernetesPropertiesMap.values().removeAll(Collections.singleton(""));

    Map<String, Object> propertiesMap =
        (Map<String, Object>)
            JsonUnflattener.unflattenAsMap(kubernetesPropertiesMap).get("kubernetes");

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        objectMapper.convertValue(propertiesMap, KubernetesConfigurationProperties.class);

    kubernetesConfigurationProperties
        .getAccounts()
        .forEach(
            acc -> {
              if (acc.getKubeconfigFile().startsWith("configserver:")) {
                acc.setKubeconfigFile(resolveConfigServerFilePath(acc.getKubeconfigFile()));
              }
            });
    return kubernetesConfigurationProperties;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getPropertiesMap() {
    ConfigurableEnvironment environment = applicationContext.getEnvironment();
    Map<String, Object> map;

    for (PropertySource<?> propertySource : environment.getPropertySources()) {

      if (propertySource instanceof BootstrapPropertySource) {
        map = (Map<String, Object>) propertySource.getSource();
        return map;
      }

      if (propertySource.getSource() instanceof BootstrapPropertySource) {
        BootstrapPropertySource<Map<String, Object>> bootstrapPropertySource =
            (BootstrapPropertySource<Map<String, Object>>) propertySource.getSource();
        return bootstrapPropertySource.getSource();
      }
    }

    throw new RuntimeException("No BootstrapPropertySource found!!!!");
  }

  private String resolveConfigServerFilePath(String key) {
    String filePath;

    if (configResourceService == null) {
      try {
        configResourceService = applicationContext.getBean(CloudConfigResourceService.class);
      } catch (BeansException e) {
        throw new ConfigFileLoadingException(
            "Config Server repository not configured for resource \"" + key + "\"");
      }
    }

    if (cacheContainsKey(key)) {
      filePath = configServerCache.get(key);
      if (resourceExist(filePath)) {
        return filePath;
      }
    }

    filePath = configResourceService.getLocalPath(key);
    addToCache(key, filePath);
    return filePath;
  }

  private boolean resourceExist(String filePath) {
    return Path.of(filePath).toFile().isFile();
  }

  private void addToCache(String key, String filePath) {
    configServerCache.put(key, filePath);
  }

  private boolean cacheContainsKey(String key) {
    if (configServerCache == null) {
      configServerCache = new HashMap<>();
      return false;
    }
    return configServerCache.containsKey(key);
  }
}
