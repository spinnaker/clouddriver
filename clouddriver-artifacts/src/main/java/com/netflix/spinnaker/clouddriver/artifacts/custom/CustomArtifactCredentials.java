/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.custom;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CustomArtifactCredentials implements ArtifactCredentials {
  @Getter private final String name;
  @Getter private final List<String> types = Collections.singletonList("custom/object");

  CustomArtifactCredentials(CustomArtifactAccount account) {
    name = account.getName();
  }

  public InputStream download(Artifact artifact) {
    throw new UnsupportedOperationException(
        "Custom references are passed on to cloud platforms to handle or process");
  }
}
