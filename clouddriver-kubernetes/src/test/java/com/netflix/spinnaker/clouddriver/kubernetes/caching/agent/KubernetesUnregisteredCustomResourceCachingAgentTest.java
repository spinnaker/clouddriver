/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesAccountProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.*;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class KubernetesUnregisteredCustomResourceCachingAgentTest {

  private static final String ACCOUNT = "my-account";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final ImmutableList<KubernetesHandler> handlers =
      ImmutableList.of(
          new KubernetesDeploymentHandler(),
          new KubernetesReplicaSetHandler(),
          new KubernetesServiceHandler(),
          new KubernetesPodHandler(),
          new KubernetesConfigMapHandler());
  private static final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap =
      new KubernetesSpinnakerKindMap(handlers);

  /**
   * kindsToCache returns only non-core kinds specified in {@link
   * KubernetesConfigurationProperties.Cache#getCacheKinds()}
   */
  @Test
  public void kindsToCacheFromConfig() {
    KubernetesConfigurationProperties configurationProperties =
        new KubernetesConfigurationProperties();
    configurationProperties.getCache().setCacheAll(false);
    configurationProperties
        .getCache()
        .setCacheKinds(Arrays.asList("deployment", "myCustomKind.my.group"));
    KubernetesUnregisteredCustomResourceCachingAgent cachingAgent =
        createCachingAgent(getNamedAccountCredentials(), configurationProperties);

    List<KubernetesKind> kindsToCache = cachingAgent.kindsToCache();

    assertThat(kindsToCache)
        .containsExactlyInAnyOrder(
            KubernetesKind.fromString("myCustomKind.my.group")); // only has CRD kinds
  }

  /**
   * Returns a KubernetesNamedAccountCredentials that contains a mock KubernetesCredentials object
   */
  private static KubernetesNamedAccountCredentials getNamedAccountCredentials() {
    KubernetesAccountProperties.ManagedAccount managedAccount =
        new KubernetesAccountProperties.ManagedAccount();
    managedAccount.setName(ACCOUNT);

    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.isValidKind(any(KubernetesKind.class))).thenReturn(true);
    KubernetesCredentials.Factory credentialFactory = mock(KubernetesCredentials.Factory.class);
    when(credentialFactory.build(managedAccount)).thenReturn(credentials);
    return new KubernetesNamedAccountCredentials(managedAccount, credentialFactory);
  }

  private static KubernetesUnregisteredCustomResourceCachingAgent createCachingAgent(
      KubernetesNamedAccountCredentials credentials,
      KubernetesConfigurationProperties configurationProperties) {
    return new KubernetesUnregisteredCustomResourceCachingAgent(
        credentials,
        objectMapper,
        new NoopRegistry(),
        0,
        1,
        10L,
        configurationProperties,
        kubernetesSpinnakerKindMap);
  }
}
