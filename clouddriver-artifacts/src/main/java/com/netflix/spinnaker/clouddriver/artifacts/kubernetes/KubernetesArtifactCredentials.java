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

package com.netflix.spinnaker.clouddriver.artifacts.kubernetes;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.docker.DockerArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class KubernetesArtifactCredentials implements ArtifactCredentials {
  private final String name;
  private final List<String> types;

  public KubernetesArtifactCredentials(KubernetesArtifactAccount account) {
    this.name = account.getName();
    this.types =
        Arrays.stream(KubernetesArtifactType.values())
            .map(KubernetesArtifactType::getType)
            .collect(Collectors.toList());
    types.remove(DockerArtifactCredentials.TYPE);
  }

  public InputStream download(Artifact artifact) {
    throw new UnsupportedOperationException(
        "Kubernetes artifacts are retrieved by kubernetes directly");
  }
}
