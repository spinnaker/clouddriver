/*
 * Copyright 2019 Google, LLC
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.GlobalResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.GlobalKubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesUnregisteredCustomResourceHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesCoreCachingAgentTest {
  private static final String ACCOUNT = "my-account";
  private static final String NAMESPACE1 = "test-namespace";
  private static final String NAMESPACE2 = "test-namespace2";
  private static final String DEPLOYMENT_NAME = "my-deployment";
  private static final String STORAGE_CLASS_NAME = "my-storage-class";

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final KubernetesKindRegistry kindRegistry =
      new KubernetesKindRegistry.Factory(
              new GlobalKubernetesKindRegistry(KubernetesKindProperties.getGlobalKindProperties()))
          .create();
  private static final ResourcePropertyRegistry resourcePropertyRegistry =
      new GlobalResourcePropertyRegistry(
          ImmutableList.of(), new KubernetesUnregisteredCustomResourceHandler());

  /** A test Deployment manifest */
  private static KubernetesManifest deploymentManifest() {
    KubernetesManifest deployment = new KubernetesManifest();
    deployment.put("metadata", new HashMap<>());
    deployment.setNamespace(NAMESPACE1);
    deployment.setKind(KubernetesKind.DEPLOYMENT);
    deployment.setApiVersion(KubernetesApiVersion.APPS_V1);
    deployment.setName(DEPLOYMENT_NAME);
    return deployment;
  }

  /** A test StorageClass manifest object */
  private static KubernetesManifest storageClassManifest() {
    KubernetesManifest storageClass = new KubernetesManifest();
    storageClass.put("metadata", new HashMap<>());
    storageClass.setKind(KubernetesKind.STORAGE_CLASS);
    storageClass.setApiVersion(KubernetesApiVersion.fromString("storage.k8s.io/v1"));
    storageClass.setName(STORAGE_CLASS_NAME);
    return storageClass;
  }

  /** Returns a mock KubernetesV2Credentials object */
  private static KubernetesV2Credentials mockKubernetesV2Credentials() {
    KubernetesV2Credentials v2Credentials = mock(KubernetesV2Credentials.class);
    when(v2Credentials.isLiveManifestCalls()).thenReturn(false);
    when(v2Credentials.getKindRegistry()).thenReturn(kindRegistry);
    when(v2Credentials.isValidKind(any(KubernetesKind.class))).thenReturn(true);
    when(v2Credentials.getDeclaredNamespaces())
        .thenReturn(ImmutableList.of(NAMESPACE1, NAMESPACE2));
    when(v2Credentials.getResourcePropertyRegistry()).thenReturn(resourcePropertyRegistry);
    when(v2Credentials.get(KubernetesKind.DEPLOYMENT, NAMESPACE1, DEPLOYMENT_NAME))
        .thenReturn(deploymentManifest());
    when(v2Credentials.get(KubernetesKind.STORAGE_CLASS, "", STORAGE_CLASS_NAME))
        .thenReturn(storageClassManifest());
    return v2Credentials;
  }

  /**
   * Returns a KubernetesNamedAccountCredentials that contains a mock KubernetesV2Credentials object
   */
  private static KubernetesNamedAccountCredentials<KubernetesV2Credentials>
      getNamedAccountCredentials() {
    KubernetesV2Credentials v2Credentials1 = mockKubernetesV2Credentials();
    KubernetesConfigurationProperties.ManagedAccount managedAccount =
        new KubernetesConfigurationProperties.ManagedAccount();
    managedAccount.setName(ACCOUNT);
    managedAccount.setProviderVersion(ProviderVersion.v2);
    KubernetesV2Credentials.Factory credentialFactory = mock(KubernetesV2Credentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(v2Credentials1);
    return new KubernetesNamedAccountCredentials<>(managedAccount, credentialFactory);
  }

  /**
   * Given a KubernetesNamedAccountCredentials object, builds a set of caching agents responsible
   * for caching the account's data and returns a collection of those agents.
   */
  private static ImmutableCollection<KubernetesCoreCachingAgent> createCachingAgents(
      KubernetesNamedAccountCredentials<KubernetesV2Credentials> credentials, int agentCount) {
    ImmutableList.Builder<KubernetesCoreCachingAgent> listBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < agentCount; i++) {
      listBuilder.add(
          new KubernetesCoreCachingAgent(
              credentials, objectMapper, new NoopRegistry(), i, agentCount, 10L));
    }
    return listBuilder.build();
  }

  /**
   * Given an on-demand cache request, constructs a set of caching agents and sends the on-demand
   * request to those agents, returning a collection of all non-null results of handing those
   * requests.
   */
  private static ImmutableList<OnDemandAgent.OnDemandResult> processOnDemandRequest(
      Map<String, String> data, ProviderCache providerCache) {
    KubernetesNamedAccountCredentials<KubernetesV2Credentials> credentials =
        getNamedAccountCredentials();

    Collection<KubernetesCoreCachingAgent> cachingAgents = createCachingAgents(credentials, 10);
    ImmutableList.Builder<OnDemandAgent.OnDemandResult> resultBuilder =
        new ImmutableList.Builder<>();
    cachingAgents.forEach(
        cachingAgent -> {
          OnDemandAgent.OnDemandResult result = cachingAgent.handle(providerCache, data);
          if (result != null) {
            resultBuilder.add(result);
          }
        });
    return resultBuilder.build();
  }

  @Test
  public void deploymentUpdate() {
    String expectedKey =
        String.format(
            "kubernetes.v2:infrastructure:deployment:%s:%s:%s",
            ACCOUNT, NAMESPACE1, DEPLOYMENT_NAME);

    ProviderCache providerCache = mock(ProviderCache.class);
    Map<String, String> data =
        ImmutableMap.of(
            "account", ACCOUNT,
            "location", NAMESPACE1,
            "name", "deployment " + DEPLOYMENT_NAME);
    ImmutableList<OnDemandAgent.OnDemandResult> results =
        processOnDemandRequest(data, providerCache);

    verify(providerCache, times(1)).putCacheData(any(String.class), any(CacheData.class));
    verify(providerCache, times(0)).evictDeletedItems(any(String.class), any());

    assertThat(results).hasSize(1);
    CacheResult cacheResult = results.get(0).getCacheResult();
    assertThat(cacheResult.getCacheResults()).isNotEmpty();

    Collection<CacheData> deployments = cacheResult.getCacheResults().get("deployment");
    assertThat(deployments).hasSize(1);

    CacheData cacheData = deployments.iterator().next();
    assertThat(cacheData.getId()).isEqualTo(expectedKey);
    assertThat(cacheData.getAttributes())
        .containsAllEntriesOf(
            ImmutableMap.of("apiVersion", KubernetesApiVersion.APPS_V1, "name", DEPLOYMENT_NAME));
  }

  @Test
  public void deploymentEviction() {
    String expectedKey =
        String.format(
            "kubernetes.v2:infrastructure:deployment:%s:%s:%s",
            ACCOUNT, NAMESPACE1, "non-existent");

    ProviderCache providerCache = mock(ProviderCache.class);
    Map<String, String> data =
        ImmutableMap.of(
            "account", ACCOUNT,
            "location", NAMESPACE1,
            "name", "deployment " + "non-existent");
    ImmutableList<OnDemandAgent.OnDemandResult> results =
        processOnDemandRequest(data, providerCache);

    verify(providerCache, times(0)).putCacheData(any(String.class), any(CacheData.class));
    verify(providerCache, times(1)).evictDeletedItems(any(String.class), any());

    assertThat(results).hasSize(1);
    Map<String, Collection<String>> evictions = results.get(0).getEvictions();
    assertThat(evictions).isNotEmpty();
    assertThat(evictions.get("deployment")).containsExactly(expectedKey);
  }

  @Test
  public void storageClassUpdate() {
    String expectedKey =
        String.format(
            "kubernetes.v2:infrastructure:storageClass:%s::%s", ACCOUNT, STORAGE_CLASS_NAME);
    ProviderCache providerCache = mock(ProviderCache.class);
    Map<String, String> data =
        ImmutableMap.of(
            "account",
            ACCOUNT,
            "location",
            NAMESPACE1,
            "name",
            "storageClass " + STORAGE_CLASS_NAME);
    ImmutableList<OnDemandAgent.OnDemandResult> results =
        processOnDemandRequest(data, providerCache);

    verify(providerCache, atLeast(1)).putCacheData(anyString(), any(CacheData.class));
    verify(providerCache, times(0)).evictDeletedItems(any(String.class), any());

    assertThat(results).isNotEmpty();
    CacheResult cacheResult = results.get(0).getCacheResult();
    assertThat(cacheResult.getCacheResults()).isNotEmpty();

    Collection<CacheData> storageClasses = cacheResult.getCacheResults().get("storageClass");
    assertThat(storageClasses).hasSize(1);

    CacheData cacheData = storageClasses.iterator().next();
    assertThat(cacheData.getId()).isEqualTo(expectedKey);
    assertThat(cacheData.getAttributes())
        .containsAllEntriesOf(
            ImmutableMap.of(
                "apiVersion",
                KubernetesApiVersion.fromString("storage.k8s.io/v1"),
                "name",
                STORAGE_CLASS_NAME));
  }

  @Test
  public void storageClassEviction() {
    String expectedKey =
        String.format("kubernetes.v2:infrastructure:storageClass:%s::%s", ACCOUNT, "non-existent");

    ProviderCache providerCache = mock(ProviderCache.class);
    Map<String, String> data =
        ImmutableMap.of(
            "account", ACCOUNT,
            "location", NAMESPACE1,
            "name", "storageClass " + "non-existent");
    ImmutableList<OnDemandAgent.OnDemandResult> results =
        processOnDemandRequest(data, providerCache);

    verify(providerCache, times(0)).putCacheData(any(String.class), any(CacheData.class));
    verify(providerCache, atLeast(1)).evictDeletedItems(any(String.class), any());

    assertThat(results).isNotEmpty();
    Map<String, Collection<String>> evictions = results.get(0).getEvictions();
    assertThat(evictions).isNotEmpty();
    assertThat(evictions.get("storageClass")).containsExactly(expectedKey);
  }

  @Test
  public void wrongAccount() {
    ProviderCache providerCache = mock(ProviderCache.class);
    Map<String, String> data =
        ImmutableMap.of(
            "account",
            "non-existent-account",
            "location",
            NAMESPACE1,
            "name",
            "deployment " + DEPLOYMENT_NAME);
    ImmutableList<OnDemandAgent.OnDemandResult> results =
        processOnDemandRequest(data, providerCache);
    assertThat(results).hasSize(0);
    verifyZeroInteractions(providerCache);
  }

  @Test
  public void wrongNamespace() {
    ProviderCache providerCache = mock(ProviderCache.class);
    Map<String, String> data =
        ImmutableMap.of(
            "account",
            ACCOUNT,
            "location",
            "non-existent-namespace",
            "name",
            "deployment " + DEPLOYMENT_NAME);
    ImmutableList<OnDemandAgent.OnDemandResult> results =
        processOnDemandRequest(data, providerCache);
    assertThat(results).hasSize(0);
    verifyZeroInteractions(providerCache);
  }
}
