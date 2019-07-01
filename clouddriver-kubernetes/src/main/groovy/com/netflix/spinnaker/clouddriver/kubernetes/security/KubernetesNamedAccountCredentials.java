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
import com.netflix.spinnaker.clouddriver.data.ConfigFileService;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Getter
public class KubernetesNamedAccountCredentials<C extends KubernetesCredentials>
    implements AccountCredentials<C> {
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
  private final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;

  public KubernetesNamedAccountCredentials(
      KubernetesConfigurationProperties.ManagedAccount managedAccount,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      CredentialFactory factory) {
    this.name = managedAccount.getName();
    this.providerVersion = managedAccount.getProviderVersion();
    this.environment =
        Optional.ofNullable(managedAccount.getEnvironment()).orElse(managedAccount.getName());
    this.accountType =
        Optional.ofNullable(managedAccount.getAccountType()).orElse(managedAccount.getName());
    this.skin =
        Optional.ofNullable(managedAccount.getSkin())
            .orElse(managedAccount.getProviderVersion().toString());
    this.cacheThreads = managedAccount.getCacheThreads();
    this.cacheIntervalSeconds = managedAccount.getCacheIntervalSeconds();
    this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;

    Permissions permissions = managedAccount.getPermissions().build();
    if (permissions.isRestricted()) {
      this.permissions = permissions;
      this.requiredGroupMembership = Collections.emptyList();
    } else {
      this.permissions = null;
      this.requiredGroupMembership =
          Collections.unmodifiableList(managedAccount.getRequiredGroupMembership());
    }

    switch (managedAccount.getProviderVersion()) {
      case v1:
        this.credentials = (C) factory.buildV1Credentials(managedAccount);
        break;
      case v2:
        this.credentials = (C) factory.buildV2Credentials(managedAccount);
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown provider type: " + managedAccount.getProviderVersion());
    }
  }

  public List<String> getNamespaces() {
    return credentials.getDeclaredNamespaces();
  }

  public Map<String, String> getSpinnakerKindMap() {
    if (kubernetesSpinnakerKindMap == null) {
      return Collections.emptyMap();
    }
    Map<String, String> kindMap =
        new HashMap<>(kubernetesSpinnakerKindMap.kubernetesToSpinnakerKindStringMap());
    C creds = getCredentials();
    if (creds instanceof KubernetesV2Credentials) {
      ((KubernetesV2Credentials) creds)
          .getCustomResources()
          .forEach(
              customResource ->
                  kindMap.put(
                      customResource.getKubernetesKind(), customResource.getSpinnakerKind()));
    }
    return kindMap;
  }

  @Component
  @RequiredArgsConstructor
  public static class CredentialFactory {
    private final String userAgent;
    private final Registry spectatorRegistry;
    private final NamerRegistry namerRegistry;
    private final AccountCredentialsRepository accountCredentialsRepository;
    private final KubectlJobExecutor jobExecutor;
    private final ConfigFileService configFileService;

    KubernetesV1Credentials buildV1Credentials(
        KubernetesConfigurationProperties.ManagedAccount managedAccount) {
      validateAccount(managedAccount);
      return new KubernetesV1Credentials(
          managedAccount.getName(),
          getKubeconfigFile(managedAccount),
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
          accountCredentialsRepository);
    }

    KubernetesV2Credentials buildV2Credentials(
        KubernetesConfigurationProperties.ManagedAccount managedAccount) {
      validateAccount(managedAccount);
      NamerRegistry.lookup()
          .withProvider(KubernetesCloudProvider.getID())
          .withAccount(managedAccount.getName())
          .setNamer(
              KubernetesManifest.class,
              namerRegistry.getNamingStrategy(managedAccount.getNamingStrategy()));
      return new KubernetesV2Credentials(
          spectatorRegistry, jobExecutor, managedAccount, getKubeconfigFile(managedAccount));
    }

    private void validateAccount(KubernetesConfigurationProperties.ManagedAccount managedAccount) {
      if (StringUtils.isEmpty(managedAccount.getName())) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      if (!managedAccount.getOmitNamespaces().isEmpty()
          && !managedAccount.getNamespaces().isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if (!managedAccount.getOmitKinds().isEmpty() && !managedAccount.getKinds().isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'kinds' and 'omitKinds' can be specified");
      }
    }

    private String getKubeconfigFile(
        KubernetesConfigurationProperties.ManagedAccount managedAccount) {
      if (StringUtils.isNotEmpty(managedAccount.getKubeconfigFile())) {
        return configFileService.getLocalPath(managedAccount.getKubeconfigFile(), "kube", "config");
      }

      if (StringUtils.isNotEmpty(managedAccount.getKubeconfigContents())) {
        return configFileService.getLocalPathForContents(
            managedAccount.getKubeconfigContents(), "kube", "config");
      }

      return System.getProperty("user.home") + "/.kube/config";
    }
  }
}
