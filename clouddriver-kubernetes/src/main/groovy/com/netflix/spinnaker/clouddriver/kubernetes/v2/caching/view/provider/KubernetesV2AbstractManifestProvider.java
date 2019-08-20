/*
 * Copyright 2018 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesAccountResolver;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ManifestProvider;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class KubernetesV2AbstractManifestProvider
    implements ManifestProvider<KubernetesV2Manifest> {
  private final KubernetesAccountResolver resourcePropertyResolver;

  public KubernetesV2AbstractManifestProvider(KubernetesAccountResolver resourcePropertyResolver) {
    this.resourcePropertyResolver = resourcePropertyResolver;
  }

  protected final ResourcePropertyRegistry getRegistry(String account) {
    return resourcePropertyResolver.getResourcePropertyRegistry(account);
  }

  protected Optional<KubernetesV2Credentials> getCredentials(String account) {
    return resourcePropertyResolver.getCredentials(account);
  }

  protected boolean isAccountRelevant(String account) {
    return getCredentials(account).isPresent();
  }

  protected boolean makesLiveCalls(String account) {
    return getCredentials(account)
        .map(KubernetesV2Credentials::isLiveManifestCalls)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Account " + account + " is not a Kubernetess v2 account"));
  }

  protected KubernetesV2Manifest buildManifest(
      String account,
      KubernetesManifest manifest,
      List<KubernetesManifest> events,
      List<KubernetesPodMetric.ContainerMetric> metrics) {
    String namespace = manifest.getNamespace();
    KubernetesKind kind = manifest.getKind();

    KubernetesResourceProperties properties = getRegistry(account).get(kind);
    if (properties == null) {
      return null;
    }

    Function<KubernetesManifest, String> lastEventTimestamp =
        (m) -> (String) m.getOrDefault("lastTimestamp", m.getOrDefault("firstTimestamp", "n/a"));

    events =
        events.stream()
            .sorted(Comparator.comparing(lastEventTimestamp))
            .collect(Collectors.toList());

    Moniker moniker = KubernetesManifestAnnotater.getMoniker(manifest);

    KubernetesHandler handler = properties.getHandler();

    return KubernetesV2Manifest.builder()
        .account(account)
        .name(manifest.getFullResourceName())
        .location(namespace)
        .manifest(manifest)
        .moniker(moniker)
        .status(handler.status(manifest))
        .artifacts(handler.listArtifacts(manifest))
        .events(events)
        .warnings(handler.listWarnings(manifest))
        .metrics(metrics)
        .build();
  }
}
