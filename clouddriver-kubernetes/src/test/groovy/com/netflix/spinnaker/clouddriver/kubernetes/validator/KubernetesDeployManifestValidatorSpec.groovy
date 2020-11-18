/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.validator

import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.validator.manifest.KubernetesDeployManifestValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors
import spock.lang.Specification

class KubernetesDeployManifestValidatorSpec extends Specification{

  KubernetesCredentials credOne = Mock(KubernetesCredentials) {
    getKindStatus(_) >> KubernetesCredentials.KubernetesKindStatus.VALID
    getNamespaces() >> ImmutableList.copyOf(["namespace2"])
  }
  AccountCredentials accountCredentials = Mock(AccountCredentials) {
    getCredentials() >> credOne
  }
  AccountCredentialsProvider provider = Mock(AccountCredentialsProvider) {
    getCredentials("account") >> accountCredentials
  }
  def validator = new KubernetesDeployManifestValidator(provider)
  def errors = Mock(DescriptionValidationErrors)


  void "should reject when overridden name is not in account's namespaces"() {
    given:
    def manifest = Mock(KubernetesManifest) {
      getKind() >> Mock(KubernetesKind)
      getNamespace() >> "namespace"
    }
    def description = Mock(KubernetesDeployManifestDescription) {
      getAccount() >> "account"
      getManifests() >> [manifest]
      getNamespaceOverride() >> "namespace"
    }

    when:
    validator.validate(null, description, errors )

    then:
    1 * errors.reject(_, "deployKubernetesManifest.deployKubernetesManifest.namespace.wrongNamespace")
  }

  void "should accept when overridden name is in account's namespaces"() {
    given:
    def manifest = Mock(KubernetesManifest) {
      getKind() >> Mock(KubernetesKind)
      getNamespace() >> "namespace2"
    }
    def description = Mock(KubernetesDeployManifestDescription) {
      getAccount() >> "account"
      getManifests() >> [manifest]
      getNamespaceOverride() >> "namespace2"
    }

    when:
    validator.validate(null, description, errors )

    then:
    1 * manifest.setNamespace("namespace2")
    0 * errors.reject(_, _)
  }

}
