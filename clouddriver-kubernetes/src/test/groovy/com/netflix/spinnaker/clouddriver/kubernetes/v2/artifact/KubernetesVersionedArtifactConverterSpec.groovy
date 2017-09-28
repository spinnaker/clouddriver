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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification

class KubernetesVersionedArtifactConverterSpec extends Specification {
  def "correctly infer versioned artifact properties"() {
    expect:
    def type = "kubernetes/$apiVersion:$kind"

    def artifact = Artifact.builder()
      .type(type)
      .name(name)
      .version(version)
      .build()

    def converter = new KubernetesVersionedArtifactConverter()
    converter.getApiVersion(artifact) == apiVersion
    converter.getKind(artifact) == kind
    converter.getDeployedName(artifact) == "$name-$version"


    where:
    apiVersion                              | kind                       | name             | version
    KubernetesApiVersion.EXTENSIONS_V1BETA1 | KubernetesKind.REPLICA_SET | "my-rs"          | "v000"
    KubernetesApiVersion.EXTENSIONS_V1BETA1 | KubernetesKind.REPLICA_SET | "my-other-rs-_-" | "v010"
  }
}
