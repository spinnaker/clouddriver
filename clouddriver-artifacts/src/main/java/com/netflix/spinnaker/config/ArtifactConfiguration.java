/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.squareup.okhttp.OkHttpClient;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Configuration
@EnableConfigurationProperties(ArtifactProviderProperties.class)
@EnableScheduling
@RequiredArgsConstructor
@Component
@ComponentScan("com.netflix.spinnaker.clouddriver.artifacts")
@Slf4j
public class ArtifactConfiguration {
  private final ArtifactProviderProperties providerProperties;

  @Bean
  OkHttpClient okHttpClient(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {
    log.info("Initializing okHttpClient for Artifact provider");
    long connectionTimeout =
        providerProperties.getConnectTimeoutMs() > 0
            ? providerProperties.getConnectTimeoutMs()
            : okHttpClientConfigurationProperties.getConnectTimeoutMs();
    long readTimeout =
        providerProperties.getReadTimeoutMs() > 0
            ? providerProperties.getReadTimeoutMs()
            : okHttpClientConfigurationProperties.getReadTimeoutMs();
    boolean retryOnConnectionFailure =
        providerProperties.isRetryOnConnectionFailure()
            || okHttpClientConfigurationProperties.isRetryOnConnectionFailure();

    OkHttpClient client = new OkHttpClient();
    client.setConnectTimeout(connectionTimeout, TimeUnit.MILLISECONDS);
    client.setReadTimeout(readTimeout, TimeUnit.MILLISECONDS);
    client.setRetryOnConnectionFailure(retryOnConnectionFailure);

    return client;
  }
}
