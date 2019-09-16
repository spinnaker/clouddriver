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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description


import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties
import spock.lang.Specification

class KubernetesKindPropertiesSpec extends Specification {
  def "creates and returns the supplied properties"() {
    when:
    def properties = KubernetesKindProperties.create(KubernetesKind.REPLICA_SET, true)

    then:
    properties.getKubernetesKind() == KubernetesKind.REPLICA_SET
    properties.isNamespaced()
    !properties.hasClusterRelationship()

    when:
    properties = KubernetesKindProperties.create(KubernetesKind.REPLICA_SET, false)

    then:
    properties.getKubernetesKind() == KubernetesKind.REPLICA_SET
    !properties.isNamespaced()
    !properties.hasClusterRelationship()
  }

  def "sets default properties to the expected values"() {
    when:
    def properties = KubernetesKindProperties.withDefaultProperties(KubernetesKind.REPLICA_SET)

    then:
    properties.isNamespaced()
    !properties.hasClusterRelationship()
  }

  def "returns expected results for built-in kinds"() {
    when:
    def defaultProperties = KubernetesKindProperties.getGlobalKindProperties()
    def replicaSetProperties = defaultProperties.stream()
      .filter({p -> p.getKubernetesKind().equals(KubernetesKind.REPLICA_SET)})
      .findFirst()
    def namespaceProperties = defaultProperties.stream()
      .filter({p -> p.getKubernetesKind().equals(KubernetesKind.NAMESPACE)})
      .findFirst()

    then:
    replicaSetProperties.isPresent()
    replicaSetProperties.get().isNamespaced()
    replicaSetProperties.get().hasClusterRelationship()

    namespaceProperties.isPresent()
    !namespaceProperties.get().isNamespaced()
    !namespaceProperties.get().hasClusterRelationship()
  }
}
