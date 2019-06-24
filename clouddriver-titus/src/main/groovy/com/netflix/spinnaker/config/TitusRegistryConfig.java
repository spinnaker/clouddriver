package com.netflix.spinnaker.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DefaultDockerOkClientProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.TitusRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@Configuration
public class TitusRegistryConfig {

  @Autowired RestAdapter.LogLevel retrofitLogLevel;

  @Autowired RequestInterceptor spinnakerRequestInterceptor;

  @Bean
  Endpoint titusRegistryEndpoint(@Value("${titus.titusRegistryUrl}") String titusRegistryBaseUrl) {
    return newFixedEndpoint(titusRegistryBaseUrl);
  }

  @Bean
  TitusRegistryService titusRegistryService(
      Endpoint titusRegistryEndpoint,
      ObjectMapper mapper,
      @Value("${titus.titusRegistryUrl}") String registryBaseUrl,
      @Value("${ok-http-client.read-timeout-ms:59000}") int readTimeoutMs) {
    return new RestAdapter.Builder()
        .setRequestInterceptor(spinnakerRequestInterceptor)
        .setEndpoint(titusRegistryEndpoint)
        .setClient(
            new DefaultDockerOkClientProvider().provide(registryBaseUrl, readTimeoutMs, true))
        .setLogLevel(retrofitLogLevel)
        .setConverter(new JacksonConverter(mapper))
        .build()
        .create(TitusRegistryService.class);
  }
}
