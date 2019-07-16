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
package com.netflix.spinnaker.clouddriver.event.config

import com.netflix.spinnaker.clouddriver.event.EventPublisher
import com.netflix.spinnaker.clouddriver.event.SpringEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Auto-configures the event sourcing library.
 *
 * TODO(rz): Should add a composite [EventPublisher] so [SpringEventPublisher] is always wired up
 */
@Configuration
@Import(MemoryEventRepositoryConfig::class)
class EventSourceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(EventPublisher::class)
  fun eventPublisher(
    applicationEventPublisher: ApplicationEventPublisher,
    eventConverters: List<SpringEventPublisher.SpinEventConverter>
  ): EventPublisher =
    SpringEventPublisher(applicationEventPublisher, eventConverters)
}
