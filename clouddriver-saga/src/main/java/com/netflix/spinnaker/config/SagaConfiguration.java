/*
 * Copyright 2019 Netflix, Inc.
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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.saga.DefaultSagaProcessor;
import com.netflix.spinnaker.clouddriver.saga.SagaProcessor;
import com.netflix.spinnaker.clouddriver.saga.interceptors.DefaultSagaInterceptor;
import com.netflix.spinnaker.clouddriver.saga.interceptors.SagaInterceptor;
import com.netflix.spinnaker.clouddriver.saga.repository.MemorySagaRepository;
import com.netflix.spinnaker.clouddriver.saga.repository.SagaRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.netflix.spinnaker.clouddriver.saga")
public class SagaConfiguration {

  @Bean
  @ConditionalOnMissingBean(SagaRepository.class)
  SagaRepository memorySagaRepository() {
    return new MemorySagaRepository();
  }

  @Bean
  SagaInterceptor defaultSagaInterceptor() {
    return new DefaultSagaInterceptor();
  }

  @Bean
  SagaProcessor defaultSagaProcessor(
      SagaRepository sagaRepository,
      Registry registry,
      ApplicationEventPublisher applicationEventPublisher,
      List<SagaInterceptor> sagaInterceptors) {
    return new DefaultSagaProcessor(
        sagaRepository, registry, applicationEventPublisher, sagaInterceptors);
  }
}
