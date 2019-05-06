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
import com.netflix.spinnaker.clouddriver.kubernetes.config.CustomKubernetesResource;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesCachingPolicy;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
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
import com.netflix.spinnaker.moniker.Namer;
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
    this.environment = environment;
    this.accountType = accountType;
    this.skin = Optional.ofNullable(skin).orElse(providerVersion.toString());
    this.cacheThreads = cacheThreads;
    this.requiredGroupMembership = requiredGroupMembership;
    this.permissions = permissions;
    this.credentials = credentials;
    this.cacheIntervalSeconds = cacheIntervalSeconds;
  }

  static class Builder<C extends KubernetesCredentials> {
    String name;
    ProviderVersion providerVersion;
    String environment;
    String accountType;
    String context;
    String cluster;
    String oAuthServiceAccount;
    List<String> oAuthScopes;
    String user;
    String userAgent;
    String kubeconfigFile;
    String kubeconfigContents;
    String kubectlExecutable;
    Integer kubectlRequestTimeoutSeconds;
    Boolean serviceAccount;
    Boolean metrics;
    Boolean configureImagePullSecrets;
    List<String> namespaces;
    List<String> omitNamespaces;
    String skin;
    int cacheThreads;
    C credentials;
    List<String> requiredGroupMembership;
    Permissions permissions;
    List<LinkedDockerRegistryConfiguration> dockerRegistries;
    Registry spectatorRegistry;
    AccountCredentialsRepository accountCredentialsRepository;
    KubectlJobExecutor jobExecutor;
    Namer namer;
    List<CustomKubernetesResource> customResources;
    List<KubernetesCachingPolicy> cachingPolicies;
    List<String> kinds;
    List<String> omitKinds;
    boolean debug;
    boolean checkPermissionsOnStartup;
    KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;
    Boolean onlySpinnakerManaged;
    Boolean liveManifestCalls;
    Long cacheIntervalSeconds;

    Builder kubernetesSpinnakerKindMap(KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap) {
      this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;
      return this;
    }

    Builder name(String name) {
      this.name = name;
      return this;
    }

    Builder providerVersion(ProviderVersion providerVersion) {
      this.providerVersion = providerVersion;
      return this;
    }

    Builder environment(String environment) {
      this.environment = environment;
      return this;
    }

    Builder accountType(String accountType) {
      this.accountType = accountType;
      return this;
    }

    Builder context(String context) {
      this.context = context;
      return this;
    }

    Builder cluster(String cluster) {
      this.cluster = cluster;
      return this;
    }

    Builder oAuthServiceAccount(String oAuthServiceAccount) {
      this.oAuthServiceAccount = oAuthServiceAccount;
      return this;
    }

    Builder oAuthScopes(List<String> oAuthScopes) {
      this.oAuthScopes = oAuthScopes;
      return this;
    }

    Builder user(String user) {
      this.user = user;
      return this;
    }

    Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    Builder kubeconfigFile(String kubeconfigFile) {
      this.kubeconfigFile = kubeconfigFile;
      return this;
    }

    Builder kubeconfigContents(String kubeconfigContents) {
      this.kubeconfigContents = kubeconfigContents;
      return this;
    }

    Builder kubectlExecutable(String kubectlExecutable) {
      this.kubectlExecutable = kubectlExecutable;
      return this;
    }

    Builder kubectlRequestTimeoutSeconds(Integer kubectlRequestTimeoutSeconds) {
      this.kubectlRequestTimeoutSeconds = kubectlRequestTimeoutSeconds;
      return this;
    }

    Builder serviceAccount(Boolean serviceAccount) {
      this.serviceAccount = serviceAccount;
      return this;
    }

    Builder metrics(Boolean metrics) {
      this.metrics = metrics;
      return this;
    }

    Builder configureImagePullSecrets(Boolean configureImagePullSecrets) {
      this.configureImagePullSecrets = configureImagePullSecrets;
      return this;
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership;
      return this;
    }

    Builder permissions(Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = Collections.emptyList();
        this.permissions = permissions;
      }
      return this;
    }

    Builder dockerRegistries(List<LinkedDockerRegistryConfiguration> dockerRegistries) {
      this.dockerRegistries = dockerRegistries;
      return this;
    }

    Builder namespaces(List<String> namespaces) {
      this.namespaces = namespaces;
      return this;
    }

    Builder omitNamespaces(List<String> omitNamespaces) {
      this.omitNamespaces = omitNamespaces;
      return this;
    }

    Builder skin(String skin) {
      this.skin = skin;
      return this;
    }

    Builder cacheThreads(int cacheThreads) {
      this.cacheThreads = cacheThreads;
      return this;
    }

    Builder credentials(C credentials) {
      this.credentials = credentials;
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

    Builder debug(boolean debug) {
      this.debug = debug;
      return this;
    }

    Builder checkPermissionsOnStartup(boolean checkPermissionsOnStartup) {
      this.checkPermissionsOnStartup = checkPermissionsOnStartup;
      return this;
    }

    Builder namer(Namer namer) {
      this.namer = namer;
      return this;
    }

    Builder cachingPolicies(List<KubernetesCachingPolicy> cachingPolicies) {
      this.cachingPolicies = cachingPolicies;
      return this;
    }

    Builder customResources(List<CustomKubernetesResource> customResources) {
      this.customResources = customResources;
      return this;
    }

    Builder kinds(List<String> kinds) {
      this.kinds = kinds;
      return this;
    }

    Builder omitKinds(List<String> omitKinds) {
      this.omitKinds = omitKinds;
      return this;
    }

    Builder onlySpinnakerManaged(Boolean onlySpinnakerManaged) {
      this.onlySpinnakerManaged = onlySpinnakerManaged;
      return this;
    }

    Builder liveManifestCalls(boolean liveManifestCalls) {
      this.liveManifestCalls = liveManifestCalls;
      return this;
    }

    Builder cacheIntervalSeconds(Long cacheIntervalSeconds) {
      this.cacheIntervalSeconds = cacheIntervalSeconds;
      return this;
    }

    private C buildCredentials() {
      switch (providerVersion) {
        case v1:
          return (C) new KubernetesV1Credentials(
              name,
              kubeconfigFile,
              context,
              cluster,
              user,
              userAgent,
              serviceAccount,
              configureImagePullSecrets,
              namespaces,
              omitNamespaces,
              dockerRegistries,
              spectatorRegistry,
              accountCredentialsRepository
          );
        case v2:
          NamerRegistry.lookup()
              .withProvider(KubernetesCloudProvider.getID())
              .withAccount(name)
              .setNamer(KubernetesManifest.class, namer);
          return (C) new KubernetesV2Credentials.Builder()
              .accountName(name)
              .kubeconfigFile(kubeconfigFile)
              .kubectlExecutable(kubectlExecutable)
              .kubectlRequestTimeoutSeconds(kubectlRequestTimeoutSeconds)
              .context(context)
              .oAuthServiceAccount(oAuthServiceAccount)
              .oAuthScopes(oAuthScopes)
              .serviceAccount(serviceAccount)
              .userAgent(userAgent)
              .namespaces(namespaces)
              .omitNamespaces(omitNamespaces)
              .registry(spectatorRegistry)
              .customResources(customResources)
              .cachingPolicies(cachingPolicies)
              .kinds(kinds)
              .omitKinds(omitKinds)
              .metrics(metrics)
              .debug(debug)
              .checkPermissionsOnStartup(checkPermissionsOnStartup)
              .jobExecutor(jobExecutor)
              .onlySpinnakerManaged(onlySpinnakerManaged)
              .liveManifestCalls(liveManifestCalls)
              .build();
        default:
          throw new IllegalArgumentException("Unknown provider type: " + providerVersion);
      }
    }

    KubernetesNamedAccountCredentials build() {
      if (StringUtils.isEmpty(name)) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      if ((omitNamespaces != null && !omitNamespaces.isEmpty()) && (namespaces != null && !namespaces.isEmpty())) {
        throw new IllegalArgumentException("At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if ((omitKinds != null && !omitKinds.isEmpty()) && (kinds != null && !kinds.isEmpty())) {
        throw new IllegalArgumentException("At most one of 'kinds' and 'omitKinds' can be specified");
      }

      if (cacheThreads == 0) {
        cacheThreads = 1;
      }

      if (providerVersion == null) {
        providerVersion = ProviderVersion.v1;
      }

      if (StringUtils.isEmpty(kubeconfigFile)){
        if (StringUtils.isEmpty(kubeconfigContents)) {
          kubeconfigFile = System.getProperty("user.home") + "/.kube/config";
        } else {
          try {
            File temp = File.createTempFile("kube", "config");
            BufferedWriter writer = new BufferedWriter(new FileWriter(temp));
            writer.write(kubeconfigContents);
            writer.close();
            kubeconfigFile = temp.getAbsolutePath();
          } catch (IOException e) {
            throw new RuntimeException("Unable to persist 'kubeconfigContents' parameter to disk: " + e.getMessage(), e);
          }
        }
      }

      if (requiredGroupMembership != null && !requiredGroupMembership.isEmpty()) {
        requiredGroupMembership = Collections.unmodifiableList(requiredGroupMembership);
      } else {
        requiredGroupMembership = Collections.emptyList();
      }

      if (configureImagePullSecrets == null) {
        configureImagePullSecrets = true;
      }

      if (serviceAccount == null) {
        serviceAccount = false;
      }

      if (metrics == null) {
        // on by default
        metrics = true;
      }

      if (credentials == null) {
        credentials = buildCredentials();
      }

      return new KubernetesNamedAccountCredentials(
        name,
        providerVersion,
        environment,
        accountType,
        skin,
        cacheThreads,
        requiredGroupMembership,
        permissions,
        credentials,
        cacheIntervalSeconds
      );
    }
  }
}
