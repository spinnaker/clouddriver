/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.fiat.model.resources.Permissions
import io.fabric8.kubernetes.client.Config

public class KubernetesNamedAccountCredentials implements AccountCredentials<KubernetesCredentials> {
  final String cloudProvider = "kubernetes"
  final String name
  final String environment
  final String accountType
  final String context
  final String cluster
  final String user
  final String userAgent
  final String kubeconfigFile
  final Boolean serviceAccount
  List<String> namespaces
  List<String> omitNamespaces
  final int cacheThreads
  KubernetesCredentials credentials
  final List<String> requiredGroupMembership
  final Permissions permissions
  final List<LinkedDockerRegistryConfiguration> dockerRegistries
  final Registry spectatorRegistry
  private final AccountCredentialsRepository accountCredentialsRepository

  public KubernetesNamedAccountCredentials(String name,
                                           AccountCredentialsRepository accountCredentialsRepository,
                                           String userAgent,
                                           String environment,
                                           String accountType,
                                           String context,
                                           String cluster,
                                           String user,
                                           String kubeconfigFile,
                                           Boolean serviceAccount,
                                           List<String> namespaces,
                                           List<String> omitNamespaces,
                                           int cacheThreads,
                                           List<LinkedDockerRegistryConfiguration> dockerRegistries,
                                           List<String> requiredGroupMembership,
                                           Permissions permissions,
                                           KubernetesCredentials credentials) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.context = context
    this.cluster = cluster
    this.user = user
    this.userAgent = userAgent
    this.kubeconfigFile = kubeconfigFile
    this.serviceAccount = serviceAccount
    this.namespaces = namespaces
    this.omitNamespaces = omitNamespaces
    this.cacheThreads = cacheThreads
    this.requiredGroupMembership = requiredGroupMembership
    this.permissions = permissions
    this.dockerRegistries = dockerRegistries
    this.accountCredentialsRepository = accountCredentialsRepository
    this.spectatorRegistry = spectatorRegistry
    this.credentials = credentials
  }

  public List<String> getNamespaces() {
    return credentials.getNamespaces()
  }
  public Registry getSpectatorRegistry() {
    return spectatorRegistry;
  }

  static class Builder {
    String name
    String environment
    String accountType
    String context
    String cluster
    String user
    String userAgent
    String kubeconfigFile
    Boolean serviceAccount
    List<String> namespaces
    List<String> omitNamespaces
    int cacheThreads
    KubernetesCredentials credentials
    List<String> requiredGroupMembership
    Permissions permissions
    List<LinkedDockerRegistryConfiguration> dockerRegistries
    Registry spectatorRegistry
    AccountCredentialsRepository accountCredentialsRepository

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder context(String context) {
      this.context = context
      return this
    }

    Builder cluster(String cluster) {
      this.cluster = cluster
      return this
    }

    Builder user(String user) {
      this.user = user
      return this
    }

    Builder userAgent(String userAgent) {
      this.userAgent = userAgent
      return this
    }

    Builder kubeconfigFile(String kubeconfigFile) {
      this.kubeconfigFile = kubeconfigFile
      return this
    }

    Builder serviceAccount(Boolean serviceAccount) {
      this.serviceAccount = serviceAccount;
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder permissions(Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = []
        this.permissions = permissions
      }
      return this
    }

    Builder dockerRegistries(List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries
      return this
    }

    Builder namespaces(List<String> namespaces) {
      this.namespaces = namespaces
      return this
    }

    Builder omitNamespaces(List<String> omitNamespaces) {
      this.omitNamespaces = omitNamespaces
      return this
    }

    Builder cacheThreads(int cacheThreads) {
      this.cacheThreads = cacheThreads
      return this
    }

    Builder credentials(KubernetesCredentials credentials) {
      this.credentials = credentials
      return this
    }

    Builder spectatorRegistry(Registry spectatorRegistry) {
      this.spectatorRegistry = spectatorRegistry
      return this
    }

    Builder accountCredentialsRepository(AccountCredentialsRepository accountCredentialsRepository) {
      this.accountCredentialsRepository = accountCredentialsRepository
      return this
    }

    private KubernetesCredentials buildCredentials() {
      Config config = KubernetesConfigParser.parse(kubeconfigFile, context, cluster, user, namespaces, serviceAccount)
      config.setUserAgent(userAgent)

      for (LinkedDockerRegistryConfiguration registry : dockerRegistries) {
        if (registry.getNamespaces() == null || registry.getNamespaces().isEmpty()) {
          registry.setNamespaces(namespaces)
        }
      }

      return new KubernetesCredentials(
          new KubernetesApiAdaptor(name, config, spectatorRegistry),
          namespaces,
          omitNamespaces,
          dockerRegistries,
          accountCredentialsRepository
      )
    }

    KubernetesNamedAccountCredentials build() {
      if (!name) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.")
      }
      if (!dockerRegistries || dockerRegistries.size() == 0) {
        throw new IllegalArgumentException("Docker registries for Kubernetes account " + name + " missing.")
      }
      if (omitNamespaces && namespaces) {
        throw new IllegalArgumentException("At most one of 'namespaces' and 'omitNamespaces' can be specified")
      }

      kubeconfigFile = kubeconfigFile != null && kubeconfigFile.length() > 0 ?
          kubeconfigFile :
          System.getProperty("user.home") + "/.kube/config"
      requiredGroupMembership = requiredGroupMembership ? Collections.unmodifiableList(requiredGroupMembership) : []
      def credentials = this.credentials ? this.credentials : buildCredentials() // this sets 'namespaces' if none are passed in,
                                                          // which is why 'buildCredentials()' is called here instead of below
      new KubernetesNamedAccountCredentials(
          name,
          accountCredentialsRepository,
          userAgent,
          environment,
          accountType,
          context,
          cluster,
          user,
          kubeconfigFile,
          serviceAccount,
          namespaces,
          omitNamespaces,
          cacheThreads,
          dockerRegistries,
          requiredGroupMembership,
          permissions,
          credentials
      )
    }
  }
}
