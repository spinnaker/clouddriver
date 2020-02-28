/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.clouddriver.yandex.provider.YandexInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.yandex.provider.agent.*;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.config.YandexCloudConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@Configuration
@Import(YandexCloudConfiguration.class)
@EnableConfigurationProperties
public class YandexInfrastructureProviderConfig {
  @Bean
  @DependsOn("yandexCloudCredentials")
  public YandexInfrastructureProvider yandexInfrastructureProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      ObjectMapper objectMapper,
      Registry registry) {
    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    Set<YandexCloudCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, YandexCloudCredentials.class);
    List<Agent> agents =
        allAccounts.stream()
            .map(
                credentials -> {
                  List<Agent> agentList = new ArrayList<>();
                  agentList.add(new YandexNetworkCachingAgent(credentials, objectMapper));
                  agentList.add(new YandexSubnetCachingAgent(credentials, objectMapper));
                  agentList.add(new YandexInstanceCachingAgent(credentials, objectMapper));
                  agentList.add(
                      new YandexServerGroupCachingAgent(credentials, registry, objectMapper));
                  agentList.add(
                      new YandexNetworkLoadBalancerCachingAgent(
                          credentials, objectMapper, registry));
                  agentList.add(new YandexImageCachingAgent(credentials, objectMapper));
                  agentList.add(new YandexServiceAccountCachingAgent(credentials, objectMapper));
                  return agentList;
                })
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    return new YandexInfrastructureProvider(agents);
  }
}
