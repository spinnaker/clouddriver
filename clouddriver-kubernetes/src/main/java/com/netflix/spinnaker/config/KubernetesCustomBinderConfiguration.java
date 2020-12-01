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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesPropertiesMapExtractor;
import com.netflix.spinnaker.clouddriver.model.PropertiesMapExtractor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"kubernetes.enabled", "custom.kubernetes.constructor.enabled"})
public class KubernetesCustomBinderConfiguration {

  @Bean
  public KubernetesPropertiesMapExtractor kubernetesPropertiesMapExtractor(
      ConfigurableApplicationContext context) {
    return new KubernetesPropertiesMapExtractor(context);
  }

  @Bean
  @RefreshScope
  public KubernetesConfigurationProperties kubernetesConfigurationProperties(
      PropertiesMapExtractor propertiesMapExtractor) {
    return new KubernetesConfigurationProperties(propertiesMapExtractor);
  }
}
