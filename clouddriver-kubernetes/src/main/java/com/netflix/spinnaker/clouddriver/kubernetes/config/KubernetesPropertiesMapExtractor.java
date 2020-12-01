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

import com.netflix.spinnaker.clouddriver.model.PropertiesMapExtractor;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileLoadingException;
import java.nio.file.Path;
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
 * class provides a Map of property keys and values that it fetches from Spring Cloud Config's
 * BootstrapPropertySource. Also adds support to existing "configserver:" feature along with caching
 * ability
 */
public class KubernetesPropertiesMapExtractor implements PropertiesMapExtractor {

  private ConfigurableApplicationContext applicationContext;
  private CloudConfigResourceService configResourceService;
  private Map<String, String> configServerCache;

  public KubernetesPropertiesMapExtractor(ConfigurableApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public Map<String, Object> getPropertiesMap() {
    ConfigurableEnvironment environment = applicationContext.getEnvironment();
    Map<String, Object> map;
    for (PropertySource<?> propertySource : environment.getPropertySources()) {
      if (propertySource instanceof BootstrapPropertySource) {
        map = (Map<String, Object>) propertySource.getSource();
        return map;
      }
      if (propertySource.getSource() instanceof BootstrapPropertySource) {
        BootstrapPropertySource bootstrapPropertySource =
            (BootstrapPropertySource) propertySource.getSource();
        if (bootstrapPropertySource.getSource() instanceof Map) {
          map = (Map<String, Object>) bootstrapPropertySource.getSource();
          return map;
        }
      }
    }
    throw new RuntimeException("No BootstrapPropertySource found!!!!");
  }

  @Override
  public String resolveConfigServerFilePath(String key) {
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
    return configResourceService.getLocalPath(key);
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
