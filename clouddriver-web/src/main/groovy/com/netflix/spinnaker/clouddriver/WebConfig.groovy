/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration
import com.netflix.spinnaker.clouddriver.configuration.ThreadPoolConfiguration
import com.netflix.spinnaker.clouddriver.filters.SimpleCORSFilter
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueueConfiguration
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.embedded.FilterRegistrationBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import javax.servlet.Filter
import javax.servlet.http.HttpServletResponse

@Configuration
@ComponentScan([
  'com.netflix.spinnaker.clouddriver.controllers',
  'com.netflix.spinnaker.clouddriver.filters',
  'com.netflix.spinnaker.clouddriver.listeners',
  'com.netflix.spinnaker.clouddriver.security',
])
@EnableConfigurationProperties([CredentialsConfiguration, ThreadPoolConfiguration, RequestQueueConfiguration])
public class WebConfig extends WebMvcConfigurerAdapter {
  @Autowired
  Registry registry

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
      new MetricsInterceptor(
        this.registry, "controller.invocations", ["account", "region"], ["BasicErrorController"]
      )
    )
  }

  @Bean
  Filter eTagFilter() {
    new ShallowEtagHeaderFilter()
  }

  @Bean
  RequestQueue requestQueue(RequestQueueConfiguration requestQueueConfiguration, Registry registry) {
    if (!requestQueueConfiguration.enabled) {
      return RequestQueue.noop()
    }

    return RequestQueue.pooled(registry, requestQueueConfiguration.timeoutMillis, requestQueueConfiguration.poolSize)
  }

  @Bean
  FilterRegistrationBean authenticatedRequestFilter() {
    def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true))
    frb.order = Ordered.HIGHEST_PRECEDENCE
    return frb
  }

  @Bean
  Filter corsFilter() {
    new SimpleCORSFilter()
  }

  @Override
  void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
    super.configureContentNegotiation(configurer)
    configurer.favorPathExtension(false);
  }

  @ControllerAdvice
  static class AccessDeniedExceptionHandler {
    @ExceptionHandler(AccessDeniedException)
    public void handle(HttpServletResponse response, AccessDeniedException ex) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage())
    }
  }
}
