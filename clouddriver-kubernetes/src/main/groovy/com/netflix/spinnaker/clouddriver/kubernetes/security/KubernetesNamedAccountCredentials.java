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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Getter
public class KubernetesNamedAccountCredentials<C extends KubernetesCredentials> implements AccountCredentials<C> {
  private final String cloudProvider = "kubernetes";
  private final String name;
  private final ProviderVersion providerVersion;
  private final String environment;
  private final String accountType;
  private final String skin;
  private final int cacheThreads;
  private final C credentials;
  private final List<String> requiredGroupMembership;
  private final Permissions permissions;
  private final Long cacheIntervalSeconds;
  KubernetesNamedAccountCredentials(String name,
                                    ProviderVersion providerVersion,
                                    String environment,
                                    String accountType,
                                    String skin,
                                    int cacheThreads,
                                    List<String> requiredGroupMembership,
                                    Permissions permissions,
                                    C credentials,
                                    Long cacheIntervalSeconds) {
    this.name = name;
    this.providerVersion = providerVersion;
    this.environment = Optional.ofNullable(environment).orElse(name);
    this.accountType = Optional.ofNullable(accountType).orElse(name);
    this.skin = Optional.ofNullable(skin).orElse(providerVersion.toString());
    this.cacheThreads = cacheThreads;
    this.credentials = credentials;
    this.cacheIntervalSeconds = cacheIntervalSeconds;

    if (permissions.isRestricted()) {
      this.permissions = permissions;
      this.requiredGroupMembership = Collections.emptyList();
    } else {
      this.permissions = null;
      this.requiredGroupMembership = Optional.ofNullable(requiredGroupMembership).map(Collections::unmodifiableList).orElse(Collections.emptyList());
    }
  }

  static class Builder<C extends KubernetesCredentials> {
    KubernetesConfigurationProperties.ManagedAccount managedAccount;
    String userAgent;
    Registry spectatorRegistry;
    NamerRegistry namerRegistry;
    AccountCredentialsRepository accountCredentialsRepository;
    KubectlJobExecutor jobExecutor;

    Builder managedAccount(KubernetesConfigurationProperties.ManagedAccount managedAccount) {
      this.managedAccount = managedAccount;
      return this;
    }

    Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    Builder spectatorRegistry(Registry spectatorRegistry) {
      this.spectatorRegistry = spectatorRegistry;
      return this;
    }

    Builder accountCredentialsRepository(AccountCredentialsRepository accountCredentialsRepository) {
      this.accountCredentialsRepository = accountCredentialsRepository;
      return this;
    }

    Builder jobExecutor(KubectlJobExecutor jobExecutor) {
      this.jobExecutor = jobExecutor;
      return this;
    }

    Builder namerRegistry(NamerRegistry namerRegistry) {
      this.namerRegistry = namerRegistry;
      return this;
    }

    private C buildCredentials() {
      if (
        managedAccount.getOmitNamespaces() != null
          && !managedAccount.getOmitNamespaces().isEmpty()
          && managedAccount.getNamespaces() != null
          && !managedAccount.getNamespaces().isEmpty()
        ) {
        throw new IllegalArgumentException("At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if (
        managedAccount.getOmitKinds() != null
          && !managedAccount.getOmitKinds().isEmpty()
          && managedAccount.getKinds() != null
          && !managedAccount.getKinds().isEmpty()
        ) {
        throw new IllegalArgumentException("At most one of 'kinds' and 'omitKinds' can be specified");
      }

      String kubeconfigFile = managedAccount.getKubeconfigFile();
      if (StringUtils.isEmpty(kubeconfigFile)){
        if (StringUtils.isEmpty(managedAccount.getKubeconfigContents())) {
          kubeconfigFile = System.getProperty("user.home") + "/.kube/config";
        } else {
          try {
            File temp = File.createTempFile("kube", "config");
            BufferedWriter writer = new BufferedWriter(new FileWriter(temp));
            writer.write(managedAccount.getKubeconfigContents());
            writer.close();
            kubeconfigFile = temp.getAbsolutePath();
          } catch (IOException e) {
            throw new RuntimeException("Unable to persist 'kubeconfigContents' parameter to disk: " + e.getMessage(), e);
          }
        }
      }
      switch (managedAccount.getProviderVersion()) {
        case v1:
          return (C) new KubernetesV1Credentials(
              managedAccount.getName(),
              kubeconfigFile,
              managedAccount.getContext(),
              managedAccount.getCluster(),
              managedAccount.getUser(),
              userAgent,
              managedAccount.getServiceAccount(),
              managedAccount.getConfigureImagePullSecrets(),
              managedAccount.getNamespaces(),
              managedAccount.getOmitNamespaces(),
              managedAccount.getDockerRegistries(),
              spectatorRegistry,
              accountCredentialsRepository
          );
        case v2:
          NamerRegistry.lookup()
              .withProvider(KubernetesCloudProvider.getID())
              .withAccount(managedAccount.getName())
              .setNamer(KubernetesManifest.class, namerRegistry.getNamingStrategy(managedAccount.getNamingStrategy()));
          return (C) new KubernetesV2Credentials.Builder()
              .accountName(managedAccount.getName())
              .kubeconfigFile(kubeconfigFile)
              .kubectlExecutable(managedAccount.getKubectlExecutable())
              .kubectlRequestTimeoutSeconds(managedAccount.getKubectlRequestTimeoutSeconds())
              .context(managedAccount.getContext())
              .oAuthServiceAccount(managedAccount.getoAuthServiceAccount())
              .oAuthScopes(managedAccount.getoAuthScopes())
              .serviceAccount(managedAccount.getServiceAccount())
              .userAgent(userAgent)
              .namespaces(managedAccount.getNamespaces())
              .omitNamespaces(managedAccount.getOmitNamespaces())
              .registry(spectatorRegistry)
              .customResources(managedAccount.getCustomResources())
              .cachingPolicies(managedAccount.getCachingPolicies())
              .kinds(managedAccount.getKinds())
              .omitKinds(managedAccount.getOmitKinds())
              .metrics(managedAccount.getMetrics())
              .debug(managedAccount.getDebug())
              .checkPermissionsOnStartup(managedAccount.getCheckPermissionsOnStartup())
              .jobExecutor(jobExecutor)
              .onlySpinnakerManaged(managedAccount.getOnlySpinnakerManaged())
              .liveManifestCalls(managedAccount.getLiveManifestCalls())
              .build();
        default:
          throw new IllegalArgumentException("Unknown provider type: " + managedAccount.getProviderVersion());
      }
    }

    KubernetesNamedAccountCredentials build() {
      if (StringUtils.isEmpty(managedAccount.getName())) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      return new KubernetesNamedAccountCredentials(
        managedAccount.getName(),
        managedAccount.getProviderVersion(),
        managedAccount.getEnvironment(),
        managedAccount.getAccountType(),
        managedAccount.getSkin(),
        managedAccount.getCacheThreads(),
        managedAccount.getRequiredGroupMembership(),
        managedAccount.getPermissions().build(),
        buildCredentials(),
        managedAccount.getCacheIntervalSeconds()
      );
    }
  }
}
