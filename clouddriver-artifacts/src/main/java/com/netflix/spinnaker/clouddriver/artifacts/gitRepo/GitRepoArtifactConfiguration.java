/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

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
@ConditionalOnProperty("artifacts.git-repo.enabled")
@EnableConfigurationProperties(GitRepoArtifactProviderProperties.class)
@RequiredArgsConstructor
@Slf4j
public class GitRepoArtifactConfiguration {
  private final GitRepoArtifactProviderProperties gitRepoArtifactProviderProperties;

  @Bean
  List<? extends GitRepoArtifactCredentials> gitRepoArtifactCredentials() {
    return gitRepoArtifactProviderProperties.getAccounts().stream()
        .map(
            a -> {
              try {
                return new GitRepoArtifactCredentials(a);
              } catch (Exception e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
