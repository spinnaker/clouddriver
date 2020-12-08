/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.config

import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.fiat.model.Authorization
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesConfigurationPropertiesSpec extends Specification {

  @Unroll
  void "two accounts' properties are properly bound to KubernetesConfigurationProperties"() {
    when:
    def RAW_REC_KIND_0 = "role"
    def RAW_REC_KIND_1 = "roleBinding"
    def RAW_REC_KIND_2 = "clusterRoleBinding"
    def ACC_0_NAME = "k8s-acc-0"
    def ACC_0_READ_0 = "acc-0-read-0-role"
    def ACC_0_READ_1 = "acc-0-read-1-role"
    def ACC_0_WRITE_0 = "acc-0-write-0-role"
    def ACC_0_WRITE_1 = "acc-0-write-1-role"
    def ACC_0_CACHE_THREADS = 2
    def ACC_0_NS_0 = "acc-0-ns-0"
    def ACC_0_NS_1 = "acc-0-ns-1"
    def ACC_0_OMIT_KIND_0 = "clusterRoleBinding"
    def ACC_0_OMIT_KIND_1 = "customResourceDefinition"
    def ACC_0_KUBECONFIG = "/tmp/config"
    def ACC_0_CRD_0_K8S_KIND = "deploymentConfig"
    def ACC_0_CRD_0_NAMESPACED = false
    def ACC_0_CRD_1_K8S_KIND = "Route"
    def ACC_0_CRD_1_VERSIONED = true
    def ACC_1_NAME = "k8s-acc-1"
    def ACC_1_READ_0 = "acc-1-read-0-role"
    def ACC_1_READ_1 = "acc-1-read-1-role"
    def ACC_1_WRITE_0 = "acc-1-write-0-role"
    def ACC_1_WRITE_1 = "acc-1-write-1-role"
    def ACC_1_CACHE_THREADS = 3
    def ACC_1_OMIT_NS_0 = "acc-1-ns-0"
    def ACC_1_OMIT_NS_1 = "acc-1-ns-1"
    def ACC_1_KIND_0 = "role"
    def ACC_1_KIND_1 = "apiService"
    def ACC_1_KUBECONFIG = "/tmp/config1"
    def ACC_1_CRD_0_K8S_KIND = "deploymentConfig"
    def ACC_1_CRD_0_NAMESPACED = false
    def ACC_1_CRD_1_K8S_KIND = "Route"
    def ACC_1_CRD_1_VERSIONED = true
    def map = new HashMap()
      map.put("kubernetes.rawResourcesEndpointConfig.kinds[0]", RAW_REC_KIND_0)
      map.put("kubernetes.rawResourcesEndpointConfig.kinds[1]", RAW_REC_KIND_1)
      map.put("kubernetes.rawResourcesEndpointConfig.kinds[2]", RAW_REC_KIND_2)
      map.put("kubernetes.accounts[0].name", ACC_0_NAME)
      map.put("kubernetes.accounts[0].permissions.READ[0]", ACC_0_READ_0)
      map.put("kubernetes.accounts[0].permissions.READ[1]", ACC_0_READ_1)
      map.put("kubernetes.accounts[0].permissions.WRITE[0]", ACC_0_WRITE_0)
      map.put("kubernetes.accounts[0].permissions.WRITE[1]", ACC_0_WRITE_1)
      map.put("kubernetes.accounts[0].cacheThreads", ACC_0_CACHE_THREADS)
      map.put("kubernetes.accounts[0].namespaces[0]", ACC_0_NS_0)
      map.put("kubernetes.accounts[0].namespaces[1]", ACC_0_NS_1)
      map.put("kubernetes.accounts[0].omitKinds[0]", ACC_0_OMIT_KIND_0)
      map.put("kubernetes.accounts[0].omitKinds[1]", ACC_0_OMIT_KIND_1)
      map.put("kubernetes.accounts[0].kubeconfigFile",ACC_0_KUBECONFIG)
      map.put("kubernetes.accounts[0].customResources[0].kubernetesKind",ACC_0_CRD_0_K8S_KIND)
      map.put("kubernetes.accounts[0].customResources[0].namespaced", ACC_0_CRD_0_NAMESPACED)
      map.put("kubernetes.accounts[0].customResources[1].kubernetesKind",ACC_0_CRD_1_K8S_KIND)
      map.put("kubernetes.accounts[0].customResources[1].versioned",ACC_0_CRD_1_VERSIONED)
      map.put("kubernetes.accounts[1].name", ACC_1_NAME)
      map.put("kubernetes.accounts[1].permissions.READ[0]", ACC_1_READ_0)
      map.put("kubernetes.accounts[1].permissions.READ[1]", ACC_1_READ_1)
      map.put("kubernetes.accounts[1].permissions.WRITE[0]", ACC_1_WRITE_0)
      map.put("kubernetes.accounts[1].permissions.WRITE[1]", ACC_1_WRITE_1)
      map.put("kubernetes.accounts[1].cacheThreads", ACC_1_CACHE_THREADS)
      map.put("kubernetes.accounts[1].omitNamespaces[0]", ACC_1_OMIT_NS_0)
      map.put("kubernetes.accounts[1].omitNamespaces[1]", ACC_1_OMIT_NS_1)
      map.put("kubernetes.accounts[1].kinds[0]", ACC_1_KIND_0)
      map.put("kubernetes.accounts[1].kinds[1]", ACC_1_KIND_1)
      map.put("kubernetes.accounts[1].kubeconfigFile",ACC_1_KUBECONFIG)
      map.put("kubernetes.accounts[1].customResources[0].kubernetesKind",ACC_1_CRD_0_K8S_KIND)
      map.put("kubernetes.accounts[1].customResources[0].namespaced", ACC_1_CRD_0_NAMESPACED)
      map.put("kubernetes.accounts[1].customResources[1].kubernetesKind",ACC_1_CRD_1_K8S_KIND)
      map.put("kubernetes.accounts[1].customResources[1].versioned",ACC_1_CRD_1_VERSIONED)

    def kubernetesConfigProvider = new BootstrapKubernetesConfigurationProvider()
    def k8sConfig = kubernetesConfigProvider.getKubernetesConfigurationProperties(map)

    then:
    k8sConfig.rawResourcesEndpointConfig.kinds == [RAW_REC_KIND_0, RAW_REC_KIND_1, RAW_REC_KIND_2] as Set
    k8sConfig.accounts.size() == 2
    k8sConfig.accounts.get(0).name == ACC_0_NAME
    k8sConfig.accounts.get(0).permissions.get(Authorization.READ) == [ACC_0_READ_0, ACC_0_READ_1]
    k8sConfig.accounts.get(0).permissions.get(Authorization.WRITE) == [ACC_0_WRITE_0, ACC_0_WRITE_1]
    k8sConfig.accounts.get(0).cacheThreads == ACC_0_CACHE_THREADS
    k8sConfig.accounts.get(0).namespaces == [ACC_0_NS_0, ACC_0_NS_1]
    k8sConfig.accounts.get(0).omitKinds == [ACC_0_OMIT_KIND_0, ACC_0_OMIT_KIND_1]
    k8sConfig.accounts.get(0).kubeconfigFile == ACC_0_KUBECONFIG
    k8sConfig.accounts.get(0).customResources.get(0).kubernetesKind == ACC_0_CRD_0_K8S_KIND
    k8sConfig.accounts.get(0).customResources.get(0).namespaced == ACC_0_CRD_0_NAMESPACED
    k8sConfig.accounts.get(0).customResources.get(1).kubernetesKind == ACC_0_CRD_1_K8S_KIND
    k8sConfig.accounts.get(0).customResources.get(1).versioned == ACC_0_CRD_1_VERSIONED

    k8sConfig.accounts.get(1).name == ACC_1_NAME
    k8sConfig.accounts.get(1).permissions.get(Authorization.READ) == [ACC_1_READ_0, ACC_1_READ_1]
    k8sConfig.accounts.get(1).permissions.get(Authorization.WRITE) == [ACC_1_WRITE_0, ACC_1_WRITE_1]
    k8sConfig.accounts.get(1).cacheThreads == ACC_1_CACHE_THREADS
    k8sConfig.accounts.get(1).omitNamespaces == [ACC_1_OMIT_NS_0, ACC_1_OMIT_NS_1]
    k8sConfig.accounts.get(1).kinds == [ACC_1_KIND_0, ACC_1_KIND_1]
    k8sConfig.accounts.get(1).kubeconfigFile == ACC_1_KUBECONFIG
    k8sConfig.accounts.get(1).customResources.get(0).kubernetesKind == ACC_1_CRD_0_K8S_KIND
    k8sConfig.accounts.get(1).customResources.get(0).namespaced == ACC_1_CRD_0_NAMESPACED
    k8sConfig.accounts.get(1).customResources.get(1).kubernetesKind == ACC_1_CRD_1_K8S_KIND
    k8sConfig.accounts.get(1).customResources.get(1).versioned == ACC_1_CRD_1_VERSIONED

  }

  @Unroll
  void "rawResourcesEndpointConfig's omitKinds set is initialized as expected"() {
    when:
    def RAW_REC_OMIT_KIND_0 = "role"
    def RAW_REC_OMIT_KIND_1 = "roleBinding"
    def RAW_REC_OMIT_KIND_2 = "clusterRoleBinding"
    def map = new HashMap()
      map.put("kubernetes.rawResourcesEndpointConfig.omitKinds[0]", RAW_REC_OMIT_KIND_0)
      map.put("kubernetes.rawResourcesEndpointConfig.omitKinds[1]", RAW_REC_OMIT_KIND_1)
      map.put("kubernetes.rawResourcesEndpointConfig.omitKinds[2]", RAW_REC_OMIT_KIND_2)

    def kubernetesConfigProvider = new BootstrapKubernetesConfigurationProvider()
    def k8sConfig = kubernetesConfigProvider.getKubernetesConfigurationProperties(map)

    then:
    k8sConfig.rawResourcesEndpointConfig.omitKinds ==
      [RAW_REC_OMIT_KIND_0, RAW_REC_OMIT_KIND_1, RAW_REC_OMIT_KIND_2] as Set
  }

}
