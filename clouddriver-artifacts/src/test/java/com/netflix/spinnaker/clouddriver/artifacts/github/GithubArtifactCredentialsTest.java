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

package com.netflix.spinnaker.clouddriver.artifacts.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.OkHttpClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.apache.commons.io.Charsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import ru.lanwen.wiremock.ext.WiremockResolver;

@ExtendWith({WiremockResolver.class, TempDirectory.class})
class GithubArtifactCredentialsTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OkHttpClient okHttpClient = new OkHttpClient();

  private final String METADATA_PATH = "/repos/spinnaker/testing/manifest.yml";
  private final String FILE_CONTENTS = "file contents";

  @Test
  void downloadWithToken(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder().name("my-github-account").token("abc").build();

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token abc")));
  }

  @Test
  void downloadWithTokenFromFile(
      @TempDirectory.TempDir Path tempDir, @WiremockResolver.Wiremock WireMockServer server)
      throws IOException {
    Path authFile = tempDir.resolve("auth-file");
    Files.write(authFile, "zzz".getBytes());

    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .tokenFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withHeader("Authorization", equalTo("token zzz")));
  }

  @Test
  void downloadWithBasicAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
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

    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder()
            .name("my-github-account")
            .usernamePasswordFile(authFile.toAbsolutePath().toString())
            .build();

    runTestCase(server, account, m -> m.withBasicAuth("someuser", "somepassw0rd!"));
  }

  @Test
  void downloadWithNoAuth(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
    GitHubArtifactAccount account =
        GitHubArtifactAccount.builder().name("my-github-account").build();

    runTestCase(server, account, m -> m.withHeader("Authorization", absent()));
  }

  private void runTestCase(
      WireMockServer server,
      GitHubArtifactAccount account,
      Function<MappingBuilder, MappingBuilder> expectedAuth)
      throws IOException {
    GitHubArtifactCredentials credentials =
        new GitHubArtifactCredentials(account, okHttpClient, objectMapper);

    Artifact artifact =
        Artifact.builder()
            .reference(server.baseUrl() + METADATA_PATH)
            .version("master")
            .type("github/file")
            .build();

    prepareServer(server, expectedAuth);

    assertThat(credentials.download(artifact))
        .hasSameContentAs(new ByteArrayInputStream(FILE_CONTENTS.getBytes(Charsets.UTF_8)));
    assertThat(server.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private void prepareServer(
      WireMockServer server, Function<MappingBuilder, MappingBuilder> withAuth) throws IOException {
    final String downloadPath = "/download/spinnaker/testing/master/manifest.yml";

    GitHubArtifactCredentials.ContentMetadata contentMetadata =
        new GitHubArtifactCredentials.ContentMetadata()
            .setDownloadUrl(server.baseUrl() + downloadPath);

    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(METADATA_PATH))
                .withQueryParam("ref", equalTo("master"))
                .willReturn(
                    aResponse().withBody(objectMapper.writeValueAsString(contentMetadata)))));

    server.stubFor(
        withAuth.apply(
            any(urlPathEqualTo(downloadPath)).willReturn(aResponse().withBody(FILE_CONTENTS))));
  }
}
