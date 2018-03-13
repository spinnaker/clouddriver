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

package com.netflix.spinnaker.clouddriver.artifacts.embedded;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
@Slf4j
public class EmbeddedArtifactConfiguration {
  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Bean
  List<? extends EmbeddedArtifactAccount> embeddedArtifactAccounts() {
    EmbeddedArtifactAccount account = new EmbeddedArtifactAccount();
    EmbeddedArtifactCredentials credentials = new EmbeddedArtifactCredentials(account);
    artifactCredentialsRepository.save(credentials);

    return Collections.singletonList(account);
  }
}
