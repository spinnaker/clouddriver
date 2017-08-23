/*
 * Copyright 2015 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.config

import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.ToString

@ToString(includeNames = true)
class KubernetesConfigurationProperties {
  @ToString(includeNames = true)
  static class ManagedAccount {
    String name
    KubernetesProviderVersion providerVersion
    String environment
    String accountType
    String context
    String cluster
    String user
    String kubeconfigFile
    Boolean serviceAccount
    List<String> namespaces
    List<String> omitNamespaces
    Integer cacheThreads
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    List<String> requiredGroupMembership
    Permissions.Builder permissions = new Permissions.Builder()
  }

  List<ManagedAccount> accounts = []
}

@ToString(includeNames = true)
class LinkedDockerRegistryConfiguration {
  String accountName
  List<String> namespaces
}

enum KubernetesProviderVersion {
  v1, v2
}
