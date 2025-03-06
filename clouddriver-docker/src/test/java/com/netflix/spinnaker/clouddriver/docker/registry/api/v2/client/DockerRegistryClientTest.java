/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactoryAutoConfiguration;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.Arrays;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@SpringBootTest(
    classes = {
      OkHttpClientConfigurationProperties.class,
      Retrofit2ServiceFactory.class,
      ServiceClientProvider.class,
      OkHttpClientProvider.class,
      OkHttpClient.class,
      DefaultServiceClientProvider.class,
      DefaultOkHttpClientBuilderProvider.class,
      Retrofit2ServiceFactoryAutoConfiguration.class,
      ObjectMapper.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class DockerRegistryClientTest {

  @RegisterExtension
  static WireMockExtension wmDockerRegistry =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static DockerRegistryClient.DockerRegistryService dockerRegistryService;
  @MockBean DockerBearerTokenService dockerBearerTokenService;
  static DockerRegistryClient dockerRegistryClient;
  @Autowired ServiceClientProvider serviceClientProvider;
  ObjectMapper objectMapper = new ObjectMapper();
  Map<String, Object> tagsResponse;
  String tagsResponseString;
  String nextLink = "</v2/library/nginx/tags/list?last=1-alpine-slim&n=5>; rel=\"next\"";

  @BeforeEach
  public void init() throws JsonProcessingException {
    tagsResponse =
        Map.of(
            "name",
            "library/nginx",
            "tags",
            new String[] {"1", "1-alpine", "1-alpine-otel", "1-alpine-perl", "1-alpine-slim"});
    tagsResponseString = objectMapper.writeValueAsString(tagsResponse);

    DockerBearerToken bearerToken = new DockerBearerToken();
    bearerToken.setToken("someToken");
    bearerToken.setAccess_token("someToken");
    Mockito.when(dockerBearerTokenService.getToken(anyString())).thenReturn(bearerToken);
    dockerRegistryService =
        buildService(DockerRegistryClient.DockerRegistryService.class, wmDockerRegistry.baseUrl());
    dockerRegistryClient =
        new DockerRegistryClient(
            wmDockerRegistry.baseUrl(), 5, "", "", dockerRegistryService, dockerBearerTokenService);
  }

  private static <T> T buildService(Class<T> type, String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(new OkHttpClient())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(type);
  }

  @Test
  public void getTagsWithoutNextLink() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(tagsResponseString)));

    DockerRegistryTags dockerRegistryTags = dockerRegistryClient.getTags("library/nginx");
    String[] tags = (String[]) tagsResponse.get("tags");
    assertIterableEquals(Arrays.asList(tags), dockerRegistryTags.getTags());
  }

  @Test
  public void getTagsWithNextLink() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("link", nextLink)
                    .withBody(tagsResponseString)));
    // TODO: Fix the below error occurring due to retrofit2 replacing `?` with `%3F`
    Assertions.assertThrows(
        SpinnakerHttpException.class,
        () -> dockerRegistryClient.getTags("library/nginx"),
        "Status: 404, Method: GET, URL: http://<baseUrl>/v2/library/nginx/tags/list%3Flast=1-alpine-slim&n=5, Message: Not Found");
  }
}
