/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.bitbucket;

import com.squareup.okhttp.OkHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty("artifacts.bitbucket.enabled")
@EnableConfigurationProperties(BitbucketArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
public class BitbucketArtifactConfiguration {
  private final BitbucketArtifactProviderProperties bitbucketArtifactProviderProperties;

  @Bean
  List<? extends BitbucketArtifactCredentials> bitbucketArtifactCredentials(OkHttpClient okHttpClient) {
    return bitbucketArtifactProviderProperties.getAccounts()
      .stream()
      .map(a -> {
        try {
          return new BitbucketArtifactCredentials(a, okHttpClient);
        } catch (Exception e) {
          log.warn("Failure instantiating Bitbucket artifact account {}: ", a, e);
          return null;
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
