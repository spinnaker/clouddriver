/*
 * Copyright 2018 Datadog, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.s3;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("artifacts.s3.enabled")
@EnableConfigurationProperties(S3ArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
class S3ArtifactConfiguration {
  private final S3ArtifactProviderProperties s3ArtifactProviderProperties;

  @Bean
  List<? extends S3ArtifactCredentials> s3ArtifactCredentials() {
    return s3ArtifactProviderProperties.getAccounts().stream()
        .map(
            a -> {
              try {
                return new S3ArtifactCredentials(a);
              } catch (IllegalArgumentException e) {
                log.warn("Failure instantiating s3 artifact account {}: ", a, e);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
