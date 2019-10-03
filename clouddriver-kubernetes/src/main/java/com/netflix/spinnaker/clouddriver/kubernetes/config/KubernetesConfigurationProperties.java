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
package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class KubernetesConfigurationProperties {
  private static final int DEFAULT_CACHE_THREADS = 1;
  private List<ManagedAccount> accounts = new ArrayList<>();

  @Data
  public static class ManagedAccount {
    private String name;
    private ProviderVersion providerVersion = ProviderVersion.v1;
    private String environment;
    private String accountType;
    private String context;
    private String cluster;
    private String oAuthServiceAccount;
    private List<String> oAuthScopes;
    private String user;
    private String kubeconfigFile;
    private String kubeconfigContents;
    private String kubectlExecutable;
    private Integer kubectlRequestTimeoutSeconds;
    private boolean serviceAccount = false;
    private boolean configureImagePullSecrets = true;
    private List<String> namespaces = new ArrayList<>();
    private List<String> omitNamespaces = new ArrayList<>();
    private String skin;
    private int cacheThreads = DEFAULT_CACHE_THREADS;
    private List<LinkedDockerRegistryConfiguration> dockerRegistries;
    private List<String> requiredGroupMembership = new ArrayList<>();
    private Permissions.Builder permissions = new Permissions.Builder();
    private String namingStrategy = "kubernetesAnnotations";
    private boolean debug = false;
    private boolean metrics = true;
    private boolean checkPermissionsOnStartup = true;
    private List<CustomKubernetesResource> customResources = new ArrayList<>();
    private List<KubernetesCachingPolicy> cachingPolicies = new ArrayList<>();
    private List<String> kinds = new ArrayList<>();
    private List<String> omitKinds = new ArrayList<>();
    private boolean onlySpinnakerManaged = false;
    private boolean liveManifestCalls = false;
    private Long cacheIntervalSeconds;
  }
}
