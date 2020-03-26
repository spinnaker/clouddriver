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

import com.google.common.collect.ImmutableList
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.AccountResourcePropertyRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiGroupSpec
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials.KubernetesKindStatus
import com.netflix.spinnaker.kork.configserver.ConfigFileService
import spock.lang.Specification

class KubernetesV2CredentialsSpec extends Specification {
  Registry registry = Stub(Registry)
  KubectlJobExecutor kubectlJobExecutor = Stub(KubectlJobExecutor)
  String NAMESPACE = "my-namespace"
  AccountResourcePropertyRegistry.Factory resourcePropertyRegistryFactory = Mock(AccountResourcePropertyRegistry.Factory)
  KubernetesKindRegistry.Factory kindRegistryFactory = new KubernetesKindRegistry.Factory(
    new GlobalKubernetesKindRegistry(KubernetesKindProperties.getGlobalKindProperties())
  )
  NamerRegistry namerRegistry = new NamerRegistry([new KubernetesManifestNamer()])
  ConfigFileService configFileService = new ConfigFileService()
  KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap = new KubernetesSpinnakerKindMap(Collections.emptyList())

  KubernetesV2Credentials.Factory credentialFactory = new KubernetesV2Credentials.Factory(
    new NoopRegistry(),
    namerRegistry,
    kubectlJobExecutor,
    configFileService,
    resourcePropertyRegistryFactory,
    kindRegistryFactory,
    kubernetesSpinnakerKindMap
  )



  void "Built-in Kubernetes kinds are considered valid by default"() {
    when:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.VALID
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "Built-in Kubernetes kinds are considered valid by default when kinds is empty"() {
    when:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        kinds: []
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.VALID
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "Only explicitly listed kinds are valid when kinds is not empty"() {
    when:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        kinds: ["deployment"]
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.VALID
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.MISSING_FROM_ALLOWED_KINDS
  }

  void "Explicitly omitted kinds are not valid"() {
    when:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        omitKinds: ["deployment"]
      ))

    then:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.EXPLICITLY_OMITTED_BY_CONFIGURATION
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "CRDs that are not installed return unknown"() {
    given:
    KubernetesApiGroup customGroup = KubernetesApiGroup.fromString("deployment.stable.example.com")
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
      name: "k8s",
      namespaces: [NAMESPACE],
      checkPermissionsOnStartup: true,
    ))

    expect:
    credentials.getKindStatus(KubernetesKind.from("my-kind", customGroup)) == KubernetesKindStatus.UNKNOWN
  }

  void "Kinds that are not readable are considered invalid"() {
    given:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
      ))
    kubectlJobExecutor.list(_ as KubernetesV2Credentials, ImmutableList.of(KubernetesKind.DEPLOYMENT), NAMESPACE, _ as KubernetesSelectorList) >> {
      throw new KubectlJobExecutor.KubectlException("Error", new Exception())
    }
    kubectlJobExecutor.list(_ as KubernetesV2Credentials, ImmutableList.of(KubernetesKind.REPLICA_SET), NAMESPACE, _ as KubernetesSelectorList) >> {
      return ImmutableList.of()
    }

    expect:
    credentials.getKindStatus(KubernetesKind.DEPLOYMENT) == KubernetesKindStatus.READ_ERROR
    credentials.getKindStatus(KubernetesKind.REPLICA_SET) == KubernetesKindStatus.VALID
  }

  void "Metrics are properly set on the account when not checking permissions"() {
    given:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: false,
        metrics: metrics
      ))

    expect:
    credentials.isMetricsEnabled() == metrics

    where:
    metrics << [true, false]
  }

  void "Metrics are properly enabled when readable"() {
    given:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
        metrics: true
      ))
    kubectlJobExecutor.topPod(_ as KubernetesV2Credentials, NAMESPACE, _) >> ImmutableList.of()

    expect:
    credentials.isMetricsEnabled() == true
  }

  void "Metrics are properly disabled when not readable"() {
    given:
    KubernetesV2Credentials credentials = credentialFactory.build(new KubernetesConfigurationProperties.ManagedAccount(
        name: "k8s",
        namespaces: [NAMESPACE],
        checkPermissionsOnStartup: true,
        metrics: true
      ))
    kubectlJobExecutor.topPod(_ as KubernetesV2Credentials, NAMESPACE, _) >> {
      throw new KubectlJobExecutor.KubectlException("Error", new Exception())
    }

    expect:
    credentials.isMetricsEnabled() == false
  }
}
