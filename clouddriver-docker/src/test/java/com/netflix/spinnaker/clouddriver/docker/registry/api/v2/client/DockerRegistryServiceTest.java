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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.io.IOException;
import java.time.Instant;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class DockerRegistryServiceTest {

  static DockerRegistryClient.DockerRegistryService dockerRegistryService;
  static TokenService tokenService;
  String service = "registry.docker.io";
  String scope = "repository:library/nginx:pull";
  String repository = "library/nginx";
  String tagsPath = "v2/library/nginx/tags/list";
  private Instant expiryTime = Instant.now();
  static String bearerToken;

  @BeforeAll
  public static void setup() {
    tokenService = buildService(TokenService.class, "https://auth.docker.io");
    dockerRegistryService =
        buildService(
            DockerRegistryClient.DockerRegistryService.class, "https://registry-1.docker.io");
  }

  @Test
  void getToken() {
    DockerBearerToken token =
        Retrofit2SyncCall.execute(tokenService.getToken("token", service, scope, "spinnaker"));
    assertNotNull(token.getAccess_token());
  }

  @Test
  void getTagsWithToken() throws IOException {
    try (ResponseBody response =
        Retrofit2SyncCall.execute(
            dockerRegistryService.getTags(repository, getBearerToken(), "Spinnaker"))) {
      assertNotNull(response.string());
    }
  }

  @Test
  void getTagsWithPathSupplied() throws IOException {
    try (ResponseBody response =
        Retrofit2SyncCall.execute(
            dockerRegistryService.get(tagsPath, getBearerToken(), "Spinnaker"))) {
      assertNotNull(response.string());
    }
  }

  private String getBearerToken() {
    if (bearerToken == null || Instant.now().isAfter(expiryTime)) {
      DockerBearerToken token =
          Retrofit2SyncCall.execute(tokenService.getToken("token", service, scope, "spinnaker"));
      bearerToken = "Bearer " + token.getAccess_token();
      this.expiryTime = Instant.now().plusSeconds(4 * 60); // 4 minutes
    }
    return bearerToken;
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

  private interface TokenService {
    @GET("/{path}")
    @Headers({"Docker-Distribution-API-Version: registry/2.0"})
    Call<DockerBearerToken> getToken(
        @Path(value = "path", encoded = true) String path,
        @Query(value = "service") String service,
        @Query(value = "scope") String scope,
        @Header("User-Agent") String agent);
  }
}
