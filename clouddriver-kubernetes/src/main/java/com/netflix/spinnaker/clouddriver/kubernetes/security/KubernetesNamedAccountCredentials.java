/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import static lombok.EqualsAndHashCode.Include;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties.ManagedAccount;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ParametersAreNonnullByDefault
public class KubernetesNamedAccountCredentials
    extends AbstractAccountCredentials<KubernetesCredentials> {
  private final String cloudProvider = "kubernetes";

  @Nonnull @Include private final String name;

  @Include private final String environment;

  @Include private final String accountType;

  @Include private final int cacheThreads;

  @Include private final KubernetesCredentials credentials;

  @Include private final List<String> requiredGroupMembership;

  @Include private final Permissions permissions;

  @Include private final Long cacheIntervalSeconds;

  public KubernetesNamedAccountCredentials(
      ManagedAccount managedAccount, KubernetesCredentials.Factory credentialFactory) {
    managedAccount.validate();
    this.name = Objects.requireNonNull(managedAccount.getName());
    this.environment =
        Optional.ofNullable(managedAccount.getEnvironment()).orElse(managedAccount.getName());
    this.accountType =
        Optional.ofNullable(managedAccount.getAccountType()).orElse(managedAccount.getName());
    this.cacheThreads = managedAccount.getCacheThreads();
    this.cacheIntervalSeconds = managedAccount.getCacheIntervalSeconds();

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

  /**
   * This method is deprecated and users should instead supply {@link
   * KubernetesNamedAccountCredentials#permissions}. In order to continue to support users who have
   * `requiredGroupMembership` in their account config, we still need to override this method. We'll
   * need to either communicate the backwards-incompatible change or translate the supplied
   * `requiredGroupMembership` into {@link KubernetesNamedAccountCredentials#permissions} before
   * removing this override.
   */
  @Override
  @SuppressWarnings("deprecation")
  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  public List<String> getNamespaces() {
    return credentials.getDeclaredNamespaces();
  }

  public Map<String, String> getSpinnakerKindMap() {
    return credentials.getSpinnakerKindMap();
  }

  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return credentials.getDockerRegistries();
  }
}
