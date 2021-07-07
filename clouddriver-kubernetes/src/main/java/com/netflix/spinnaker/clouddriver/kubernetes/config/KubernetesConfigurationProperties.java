/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.google.common.base.Strings;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class KubernetesConfigurationProperties {
  private static final int DEFAULT_CACHE_THREADS = 1;
  private List<ManagedAccount> accounts = new ArrayList<>();
  private KubernetesJobExecutorProperties jobExecutor = new KubernetesJobExecutorProperties();

  @Data
  public static class ManagedAccount implements CredentialsDefinition {
    private String name;
    private String environment;
    private String accountType;
    private String context;
    private String oAuthServiceAccount;
    private List<String> oAuthScopes;
    private String kubeconfigFile;
    private String kubeconfigContents;
    private String kubectlExecutable;
    private Integer kubectlRequestTimeoutSeconds;
    private boolean serviceAccount = false;
    private List<String> namespaces = new ArrayList<>();
    private List<String> omitNamespaces = new ArrayList<>();
    private int cacheThreads = DEFAULT_CACHE_THREADS;
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
    private Long cacheIntervalSeconds;
    private boolean cacheAllApplicationRelationships = false;
    private RawResourcesEndpointConfig rawResourcesEndpointConfig =
        new RawResourcesEndpointConfig();

    public void validate() {
      if (Strings.isNullOrEmpty(name)) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      if (!omitNamespaces.isEmpty() && !namespaces.isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if (!omitKinds.isEmpty() && !kinds.isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'kinds' and 'omitKinds' can be specified");
      }
      rawResourcesEndpointConfig.validate();
    }
  }

  @Data
  public static class KubernetesJobExecutorProperties {
    private Retries retries = new Retries();

    @Data
    public static class Retries {
      // flag to turn on/off kubectl retry on errors capability.
      private boolean enabled = false;

      // total number of attempts that are made to complete a kubectl call
      int maxAttempts = 3;

      // time in ms to wait before subsequent retry attempts
      long backOffInMs = 5000;

      // list of error strings on which to retry since kubectl binary returns textual error messages
      // back
      List<String> retryableErrorMessages = List.of("TLS handshake timeout");

      // flag to enable exponential backoff - only applicable when enableRetries: true
      boolean exponentialBackoffEnabled = false;

      // only applicable when exponentialBackoff = true
      int exponentialBackoffMultiplier = 2;

      // only applicable when exponentialBackoff = true
      long exponentialBackOffIntervalMs = 10000;
    }
  }
}
