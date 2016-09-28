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

package com.netflix.spinnaker.clouddriver.core

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.squareup.okhttp.ConnectionPool
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
class RetrofitConfig {
  @Autowired
  OkHttpClientConfiguration okHttpClientConfig

  @Value('${okHttpClient.connectionPool.maxIdleConnections:5}')
  int maxIdleConnections

  @Value('${okHttpClient.connectionPool.keepAliveDurationMs:300000}')
  int keepAliveDurationMs

  @Value('${okHttpClient.retryOnConnectionFailure:true}')
  boolean retryOnConnectionFailure

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @Bean RestAdapter.LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:BASIC}') String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel)
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkClient okClient() {
    def client = okHttpClientConfig.create()
    client.connectionPool = new ConnectionPool(maxIdleConnections, keepAliveDurationMs)
    client.retryOnConnectionFailure = retryOnConnectionFailure
    return new OkClient(client)
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  Front50Service front50Service(@Value('${services.front50.baseUrl}') String front50BaseUrl, RestAdapter.LogLevel retrofitLogLevel) {
    def endpoint = newFixedEndpoint(front50BaseUrl)
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(endpoint)
      .setClient(okClient())
      .setConverter(new JacksonConverter())
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(Front50Service))
      .build()
      .create(Front50Service)
  }

  static class Slf4jRetrofitLogger implements RestAdapter.Log {
    private final Logger logger

    public Slf4jRetrofitLogger(Class type) {
      this(LoggerFactory.getLogger(type))
    }

    public Slf4jRetrofitLogger(Logger logger) {
      this.logger = logger
    }

    @Override
    void log(String message) {
      logger.info(message)
    }
  }
}
