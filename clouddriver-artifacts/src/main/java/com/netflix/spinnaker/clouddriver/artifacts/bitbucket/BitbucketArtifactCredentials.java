/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.bitbucket;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.config.SimpleHttpArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.squareup.okhttp.Headers;
import com.squareup.okhttp.OkHttpClient;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

@NonnullByDefault
@Slf4j
public class BitbucketArtifactCredentials
    extends SimpleHttpArtifactCredentials<BitbucketArtifactAccount> implements ArtifactCredentials {
  public static final String CREDENTIALS_TYPE = "artifacts-bitbucket";
  @Getter private final String name;
  @Getter private final ImmutableList<String> types = ImmutableList.of("bitbucket/file");

  BitbucketArtifactCredentials(BitbucketArtifactAccount account, OkHttpClient okHttpClient) {
    super(okHttpClient, account);
    this.name = account.getName();
  }

  @Override
  protected Headers getHeaders(BitbucketArtifactAccount account) {
    Headers.Builder headers = new Headers.Builder();
    Optional<String> token = account.getTokenAsString();
    if (token.isPresent()) {
      headers.set(AUTHORIZATION, "Bearer " + token.get());
      headers.set(ACCEPT, MediaType.APPLICATION_JSON_VALUE);
      log.info("Loaded credentials for Bitbucket Artifact Account {}", account.getName());
      return headers.build();
    }
    return super.getHeaders(account);
  }

  @Override
  public String getType() {
    return CREDENTIALS_TYPE;
  }
}
