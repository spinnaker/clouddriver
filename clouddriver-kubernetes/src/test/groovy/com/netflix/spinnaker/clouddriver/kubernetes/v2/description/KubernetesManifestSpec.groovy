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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description

import com.fasterxml.jackson.databind.ObjectMapper
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class KubernetesManifestSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml()

  def NAME = "my-name"
  def NAMESPACE = "my-namespace"
  def KIND = "ReplicaSet"
  def API_VERSION = KubernetesApiVersion.EXTENSIONS_V1BETA1

  def BASIC_REPLICA_SET = """
apiVersion: $API_VERSION
kind: $KIND
metadata:
  name: $NAME
  namespace: $NAMESPACE
"""

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  void "correctly reads fields from basic manifest definition"() {
    when:
    KubernetesManifest manifest = stringToManifest(BASIC_REPLICA_SET)

    then:
    manifest.getName() == NAME
    manifest.getNamespace() == NAMESPACE
    manifest.getKind() == KIND
    manifest.getApiVersion() == API_VERSION
  }
}
