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

import static lombok.EqualsAndHashCode.Include;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class KubernetesNamedAccountCredentials<C extends KubernetesCredentials>
    implements AccountCredentials<C> {
  private final String cloudProvider = "kubernetes";

  @Include private final String name;

  @Include private final ProviderVersion providerVersion;

  @Include private final String environment;

  @Include private final String accountType;

  @Include private final String skin;

  @Include private final int cacheThreads;

  @Include private final C credentials;

  @Include private final List<String> requiredGroupMembership;

  @Include private final Permissions permissions;

  @Include private final Long cacheIntervalSeconds;

  private final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;

  public KubernetesNamedAccountCredentials(
      KubernetesConfigurationProperties.ManagedAccount managedAccount,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      KubernetesCredentialFactory<C> credentialFactory) {
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
    this.credentials = credentialFactory.build(managedAccount);
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
}
