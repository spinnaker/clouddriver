/*
 * Copyright 2016 Netflix, Inc.
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
 */

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchEntityTagger;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty({"elasticSearch.connection"})
@ComponentScan({"com.netflix.spinnaker.clouddriver.elasticsearch"})
@EnableConfigurationProperties(ElasticSearchConfigProperties.class)
public class ElasticSearchConfig {
  @Bean
  JestClient jestClient(ElasticSearchConfigProperties elasticSearchConfigProperties) {
    String elasticSearchConnection = elasticSearchConfigProperties.getConnection();

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(
      (new HttpClientConfig.Builder(elasticSearchConnection)).multiThreaded(true).build()
    );
    return factory.getObject();
  }

  @Bean
  ElasticSearchEntityTagger elasticSearchEntityTagger(RetrySupport retrySupport,
                                                      Front50Service front50Service,
                                                      AccountCredentialsProvider accountCredentialsProvider,
                                                      ElasticSearchEntityTagsProvider elasticSearchEntityTagsProvider) {
    return new ElasticSearchEntityTagger(
      retrySupport, front50Service, accountCredentialsProvider, elasticSearchEntityTagsProvider
    );
  }
}
