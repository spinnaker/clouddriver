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

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Configuration
@EnableScheduling
@Component
@EnableConfigurationProperties(ArtifactProviderProperties.class)
@ComponentScan("com.netflix.spinnaker.clouddriver.artifacts")
@Slf4j
public class ArtifactConfiguration {

  @Bean
  OkHttpClient okHttpClient(
      OkHttp3ClientConfiguration okHttp3ClientConfiguration,
      ArtifactProviderProperties properties) {
    log.info("Initializing for Artifact provider okhttp client");
    OkHttpClient.Builder builder = okHttp3ClientConfiguration.create();
    builder.readTimeout(properties.getReadTimeoutMs(), TimeUnit.MILLISECONDS);
    builder.connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
    builder.retryOnConnectionFailure(properties.isRetryOnConnectionFailure());
    return builder.build();
  }
}
