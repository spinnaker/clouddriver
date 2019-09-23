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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.GlobalKubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import spock.lang.Specification

class KubernetesV2CredentialsSpec extends Specification {
  Registry registry = Stub(Registry)
  KubectlJobExecutor kubectlJobExecutor = Stub(KubectlJobExecutor)
  String NAMESPACE = "my-namespace"
  AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory = Mock(AccountResourcePropertyRegistry.Factory)
  KubernetesKindRegistry.Factory kindRegistryFactory = new KubernetesKindRegistry.Factory(
    new GlobalKubernetesKindRegistry(KubernetesKindProperties.getGlobalKindProperties())
  )


  private buildCredentials(KubernetesConfigurationProperties.ManagedAccount managedAccount) {
    return new KubernetesV2Credentials(registry, kubectlJobExecutor, managedAccount, resourcePropertyRegistryFactory, kindRegistryFactory.create(), null)
  }

  void "Built-in Kubernetes kinds are considered valid by default"() {
    when:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
      )
    )

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == true
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Built-in Kubernetes kinds are considered valid by default when kinds is empty"() {
    when:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        kinds: []
      )
    )

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == true
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Only explicitly listed kinds are valid when kinds is not empty"() {
    when:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        kinds: ["deployment"]
      )
    )

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == true
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == false
  }

  void "Explicitly omitted kinds are not valid"() {
    when:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        omitKinds: ["deployment"]
      )
    )

    then:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == false
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Kinds that are not readable are considered invalid"() {
    given:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
      )
    )
    kubectlJobExecutor.list(_ as KubernetesV2Credentials, [KubernetesKind.DEPLOYMENT], NAMESPACE, _ as KubernetesSelectorList) >> {
      throw new KubectlJobExecutor.KubectlException("Error", new Exception())
    }
    kubectlJobExecutor.list(_ as KubernetesV2Credentials, [KubernetesKind.REPLICA_SET], NAMESPACE, _ as KubernetesSelectorList) >> {
      return Collections.emptyList()
    }

    expect:
    credentials.isValidKind(KubernetesKind.DEPLOYMENT) == false
    credentials.isValidKind(KubernetesKind.REPLICA_SET) == true
  }

  void "Metrics are properly set on the account when not checking permissions"() {
    given:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        metrics: metrics
      )
    )

    expect:
    credentials.isMetricsEnabled() == metrics

    where:
    metrics << [true, false]
  }

  void "Metrics are properly enabled when readable"() {
    given:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
        metrics: true
      )
    )
    kubectlJobExecutor.topPod(_ as KubernetesV2Credentials, NAMESPACE, _) >> Collections.emptyList()

    expect:
    credentials.isMetricsEnabled() == true
  }

  void "Metrics are properly disabled when not readable"() {
    given:
    KubernetesV2Credentials credentials = buildCredentials(
      new KubernetesConfigurationProperties.ManagedAccount(
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
        metrics: true
      )
    )
    kubectlJobExecutor.topPod(_ as KubernetesV2Credentials, NAMESPACE, _) >> {
      throw new KubectlJobExecutor.KubectlException("Error", new Exception())
    }

    expect:
    credentials.isMetricsEnabled() == false
  }
}
