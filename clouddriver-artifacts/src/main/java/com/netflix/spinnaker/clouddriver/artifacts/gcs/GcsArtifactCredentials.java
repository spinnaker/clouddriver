/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.gcs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@NonnullByDefault
@Slf4j
public class GcsArtifactCredentials implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-gcs";
  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of("gcs/object");

  @JsonIgnore private final Storage storage;

  GcsArtifactCredentials(String applicationName, GcsArtifactAccount account)
      throws IOException, GeneralSecurityException {
    HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    Optional<String> credentialsPath = account.getJsonPath();

    GoogleCredentials credentials;
    if (credentialsPath.isPresent()) {
      FileInputStream stream = new FileInputStream(credentialsPath.get());
      credentials =
          GoogleCredentials.fromStream(stream)
              .createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_READ_ONLY));

      log.info("Loaded credentials from {}", credentialsPath);
    } else {
      log.info(
          "artifacts.gcs.enabled without artifacts.gcs.[].jsonPath. Using default application credentials.");

      credentials = GoogleCredentials.getApplicationDefault();
    }

    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

    name = account.getName();
    storage =
        new Storage.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName(applicationName)
            .build();
  }

  public InputStream download(Artifact artifact) throws IOException {
    String reference = artifact.getReference();
    Long generation = null;
    if (reference.startsWith("gs://")) {
      reference = reference.substring("gs://".length());
    }

    int slash = reference.indexOf("/");
    if (slash <= 0) {
      throw new IllegalArgumentException(
          "GCS references must be of the format gs://<bucket>/<file-path>, got: " + artifact);
    }

    String bucketName = reference.substring(0, slash);
    String path = reference.substring(slash + 1);

    int pound = path.lastIndexOf("#");
    if (pound >= 0) {
      generation = Long.valueOf(path.substring(pound + 1));
      path = path.substring(0, pound);
    }

    Storage.Objects.Get get = storage.objects().get(bucketName, path).setGeneration(generation);

    return get.executeMediaAsInputStream();
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }
}
