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

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;


import java.util.Collections;
import java.util.List;

@Configuration
@ConditionalOnProperty("artifacts.s3.enabled")
@EnableScheduling
@Slf4j
public class S3ArtifactConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("artifacts.s3")
  S3ArtifactProviderProperties s3ArtifactProviderProperties() { return new S3ArtifactProviderProperties(); }

  @Autowired
  S3ArtifactProviderProperties s3ArtifactProviderProperties;

  @Autowired
  ArtifactCredentialsRepository artifactCredentialsRepository;

  @Bean
  List<? extends S3ArtifactAccount> s3ArtifactCredentials() {
    S3ArtifactAccount account = new S3ArtifactAccount();
    S3ArtifactCredentials creds = new S3ArtifactCredentials(account);
    artifactCredentialsRepository.save(creds);

    return Collections.singletonList(account);
  }
}
