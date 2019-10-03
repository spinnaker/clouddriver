/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.validator

import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nullable

class KubernetesValidationUtilSpec extends Specification {
  @Unroll
  void "wiring of kind/namespace validation"() {
    given:
    Errors errors = Mock(Errors)
    String kubernetesAccount = "testAccount"
    def namespaces = ImmutableList.of("test-namespace")
    def omitNamespaces = ImmutableList.of("omit-namespace")
    def kind = KubernetesKind.DEPLOYMENT
    AccountCredentials accountCredentials = Mock(AccountCredentials)
    KubernetesV2Credentials credentials = Mock(KubernetesV2Credentials)
    KubernetesValidationUtil kubernetesValidationUtil = new KubernetesValidationUtil("currentContext", errors);
    AccountCredentialsProvider accountCredentialsProvider = Mock(AccountCredentialsProvider)
    KubernetesManifest manifest = Mock(KubernetesManifest)

    when:
    def judgement = kubernetesValidationUtil.validateV2Credentials(accountCredentialsProvider, kubernetesAccount, manifest)

    then:
    accountCredentialsProvider.getCredentials(kubernetesAccount) >> accountCredentials
    accountCredentials.getCredentials() >> credentials
    credentials.getOmitNamespaces() >> omitNamespaces
    credentials.namespaces >> namespaces
    manifest.getNamespace() >> testNamespace
    manifest.getKind() >> kind
    credentials.isValidKind(kind) >> true
    judgement == expectedResult

    where:
    testNamespace       || expectedResult
    null                || true
    ""                  || true
    "test-namespace"    || true
    "omit-namespace"    || false
    "unknown-namespace" || false
  }

  @Unroll
  void "validation of namespaces"() {
    given:
    Errors errors = Mock(Errors)
    KubernetesV2Credentials credentials = Mock(KubernetesV2Credentials)
    KubernetesValidationUtil kubernetesValidationUtil = new KubernetesValidationUtil("currentContext", errors);

    when:
    def judgement = kubernetesValidationUtil.validateNamespace(testNamespace, credentials)

    then:
    credentials.getOmitNamespaces() >> toImmutableList(omitNamespaces)
    credentials.namespaces >> toImmutableList(namespaces)
    judgement == allowedNamespace

    where:
    namespaces         | omitNamespaces     | testNamespace       || allowedNamespace
    ["test-namespace"] | ["omit-namespace"] | "test-namespace"    || true
    null               | ["omit-namespace"] | "test-namespace"    || true
    ["test-namespace"] | null               | "test-namespace"    || true
    ["test-namespace"] | ["omit-namespace"] | "omit-namespace"    || false
    null               | ["omit-namespace"] | "omit-namespace"    || false
    ["test-namespace"] | ["omit-namespace"] | "unknown-namespace" || false
    null               | null               | "unknown-namespace" || true
    // When namespaces is not specified (and we rely on dynamic discovery) we need to treat an unknown namespace as
    // allowed. This is because we might be adding the namespace as part of the same deploy operation, so can't rely
    // on looking in the namespace cache for the unknown namespace.
    []                 | []                 | "unknown-namespace" || true
    []                 | ["omit-namespace"] | "unknown-namespace" || true
  }

  @Nullable
  private static <T> ImmutableList<T> toImmutableList(@Nullable Iterable<T> list) {
    if (list == null) {
      return null;
    }
    return ImmutableList.copyOf(list);
  }
}
