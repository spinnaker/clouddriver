/*
 * Copyright 2017 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.configuration

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import groovy.util.logging.Slf4j
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.config.client.ConfigClientProperties
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
/**
 * Configures Spring Cloud Config Client to speak to a Config Server over an SSL encrypted channel.
 *
 * NOTE: By default, spring.cloud.config.enabled=true activates Config Client, but WITHOUT SSL. TO
 *       have Config Client with SSL, use config-client.ssl.enabled=true instead.
 *
 * For property settings, see commented out settings in bootstrap.yml.
 *
 * NOTE: This is independent of serving requests via SSL.
 */
@Slf4j
@Configuration
@ConditionalOnProperty('okHttpClient.keyStore')
class CustomConfigClientConfiguration {

	private final OkHttpClientConfigurationProperties okHttpClientConfigurationProperties
	private final OkHttpClientConfiguration okHttpClientConfiguration

	@Autowired
	public CustomConfigClientConfiguration(OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
										   OkHttpClientConfiguration okHttpClientConfiguration) {
		this.okHttpClientConfigurationProperties = okHttpClientConfigurationProperties
		this.okHttpClientConfiguration = okHttpClientConfiguration
	}


	@Bean
	ConfigClientProperties configClientProperties(Environment env) {
		ConfigClientProperties configClientProperties = new ConfigClientProperties(env)
		configClientProperties.enabled = false
		configClientProperties
	}

	@Bean
	ConfigServicePropertySourceLocator configServicePropertySourceLocator(ConfigClientProperties clientProperties,
																		  RestTemplate restTemplate) {
		ConfigServicePropertySourceLocator configServicePropertySourceLocator = new ConfigServicePropertySourceLocator(clientProperties)
		configServicePropertySourceLocator.restTemplate = restTemplate
		configServicePropertySourceLocator
	}

	@Bean
	RestTemplate restTemplate(ClientHttpRequestFactory clientHttpRequestFactory) {
		new RestTemplate(clientHttpRequestFactory)
	}

	@Bean
	ClientHttpRequestFactory clientHttpRequestFactory(HttpClient httpClient) {
		new HttpComponentsClientHttpRequestFactory(httpClient)
	}

	@Bean
	HttpClient httpClient() {
		HttpClients.custom().setSSLContext(okHttpClientConfiguration.create().sslSocketFactory).build()
	}

}
