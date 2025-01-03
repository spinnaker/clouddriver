/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.artifacts.bitbucket;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import org.springframework.http.MediaType;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class, TempDirectory.class})
class BitbucketArtifactCredentialsTest {
  private final OkHttpClient okHttpClient = new OkHttpClient();

  private final String DOWNLOAD_PATH = "/repos/spinnaker/testing/manifest.yml";
  private final String FILE_CONTENTS = "file contents";

  @Test
  void downloadWithToken(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    BitbucketArtifactAccount account =
        BitbucketArtifactAccount.builder().name("my-bitbucket-account").token("abc").build();

    runTestCase(
        server,
        account,
        m ->
            m.withHeader(AUTHORIZATION, equalTo("Bearer abc"))
                .withHeader(ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE)));
  }

  @Test
  void downloadWithTokenFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    BitbucketArtifactAccount account =
        BitbucketArtifactAccount.builder()
            .name("my-bitbucket-account")
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(
        server,
        account,
        m ->
            m.withHeader(AUTHORIZATION, equalTo("Bearer zzz"))
                .withHeader(ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE)));
  }

  @Test
  void downloadWithTokenFromFileWithReloadHeaders(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    BitbucketArtifactAccount account =
        BitbucketArtifactAccount.builder()
            .name("my-bitbucket-account")
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(
        server,
        account,
        m ->
            m.withHeader(AUTHORIZATION, equalTo("Bearer zzz"))
                .withHeader(ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE)));

    Files.write(authFile, "aaa".getBytes());

    runTestCase(
        server,
        account,
        m ->
            m.withHeader(AUTHORIZATION, equalTo("Bearer aaa"))
                .withHeader(ACCEPT, equalTo(MediaType.APPLICATION_JSON_VALUE)));
  }

  @Test
  void downloadWithBasicAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    BitbucketArtifactAccount account =
        BitbucketArtifactAccount.builder()
            .name("my-bitbucket-account")
            .username("user")
            .password("passw0rd")
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("user", "passw0rd"));
  }

  @Test
  void downloadWithBasicAuthFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "someuser:somepassw0rd!".getBytes());

    BitbucketArtifactAccount account =
        BitbucketArtifactAccount.builder()
            .name("my-bitbucket-account")
            .usernamePasswordFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("someuser", "somepassw0rd!"));
  }

  @Test
  void downloadWithNoAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    BitbucketArtifactAccount account =
        BitbucketArtifactAccount.builder().name("my-bitbucket-account").build();

    runTestCase(server, account, m -> m.withHeader(AUTHORIZATION, absent()));
  }

  private void runTestCase(
      WireMockServer server,
      BitbucketArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    BitbucketArtifactCredentials credentials =
        new BitbucketArtifactCredentials(account, okHttpClient);

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + DOWNLOAD_PATH)
            .version("master")
            .type("bitbucket/file")
            .build();

    prepareServer(server, expectedAuth);

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(StandardCharsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private void prepareServer(
      WireMockServer server, Function<MappingBuilder, MappingBuilder> withAuth) {
    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(DOWNLOAD_PATH)).willReturn(aResponse().withBody(FILE_CONTENTS))));
  }
}
