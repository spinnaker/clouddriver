/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest

import com.netflix.spinnaker.moniker.Moniker
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesManifestLabelerSpec extends Specification {

  @Unroll
  void "manifests are annotated and deannotated symmetrically"() {
    given:
    def moniker = Moniker.builder().build()
    def labels = ["some-key": "some-value"]

    when:
    KubernetesManifestLabeler.storeLabels(managedBySuffix, labels, moniker)

    then:
    labels["some-key"] == "some-value"
    labels["app.kubernetes.io/managed-by"] == expectedManagedByLabel

    where:
    managedBySuffix || expectedManagedByLabel
    null            || "spinnaker"
    ""              || "spinnaker"
    "custom"        || "spinnaker-custom"
  }
}
