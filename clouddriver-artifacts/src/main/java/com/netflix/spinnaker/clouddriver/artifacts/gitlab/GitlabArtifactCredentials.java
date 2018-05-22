/*
 * Copyright 2018 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.Response;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Data
public class GitlabArtifactCredentials implements ArtifactCredentials {
  private final String name;

  @JsonIgnore
  private final Builder requestBuilder;

  @JsonIgnore
  OkHttpClient okHttpClient;

  @JsonIgnore
  ObjectMapper objectMapper;

  public GitlabArtifactCredentials(GitlabArtifactAccount account, OkHttpClient okHttpClient, ObjectMapper objectMapper) {
    this.name = account.getName();
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
    Builder builder = new Request.Builder();
    boolean useToken = !StringUtils.isEmpty(account.getToken());
    boolean useTokenFile = !StringUtils.isEmpty(account.getTokenFile());
    boolean useAuth =  useToken || useTokenFile;
    if (useAuth) {
      String authHeader = "";
      if (useTokenFile) {
        authHeader = credentialsFromFile(account.getTokenFile());
      } else if (useToken) {
        authHeader = account.getToken();
      }

      builder.header("Private-Token", authHeader);
      log.info("Loaded credentials for Gitlab Artifact Account {}", account.getName());
    } else {
      log.info("No credentials included with Gitlab Artifact Account {}", account.getName());
    }
    requestBuilder = builder;
  }

  private String credentialsFromFile(String filename) {
    try {
      String credentials = FileUtils.readFileToString(new File(filename));
      return credentials.replace("\n", "");
    } catch (IOException e) {
      log.error("Could not read Gitlab credentials file {}", filename);
      return null;
    }
  }

  public InputStream download(Artifact artifact) throws IOException {
    HttpUrl.Builder fileUrl;
    try {
      // reference should use the Gitlab raw file download url: https://docs.gitlab.com/ee/api/repository_files.html#get-raw-file-from-repository
      fileUrl = HttpUrl.parse(artifact.getReference()).newBuilder();
    } catch (Exception e) {
      throw new IllegalArgumentException("Malformed gitlab content URL in 'reference'. Read more here https://www.spinnaker.io/reference/artifacts/types/gitlab-file/: " + e.getMessage(), e);
    }

    String version = artifact.getVersion();
    if (StringUtils.isEmpty(version)) {
      log.info("No version specified for artifact {}, using 'master'.", version);
      version = "master";
    }

    fileUrl.addQueryParameter("ref", version);
    Request fileRequest = requestBuilder
      .url(fileUrl.build().toString())
      .build();

    try {
      Response downloadResponse = okHttpClient.newCall(fileRequest).execute();
      return downloadResponse.body().byteStream();
    } catch (IOException e) {
      throw new com.netflix.spinnaker.clouddriver.artifacts.gitlab.GitlabArtifactCredentials.FailedDownloadException("Unable to download the contents of artifact " + artifact + ": " + e.getMessage(), e);
    }
  }

  @Override
  public boolean handlesType(String type) {
    return type.equals("gitlab/file");
  }

  public class FailedDownloadException extends IOException {

    public FailedDownloadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
