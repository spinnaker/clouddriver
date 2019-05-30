/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.DEPLOY_MANIFEST;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesV2ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@KubernetesOperation(DEPLOY_MANIFEST)
@Component
public class KubernetesDeployManifestConverter extends AbstractAtomicOperationsCredentialsSupport {

  private static final String KIND_VALUE_LIST = "list";
  private static final String KIND_LIST_ITEMS_KEY = "items";

  private final KubernetesResourcePropertyRegistry registry;

  private final KubernetesV2ArtifactProvider artifactProvider;

  @Autowired
  public KubernetesDeployManifestConverter(
      AccountCredentialsProvider accountCredentialsProvider,
      ObjectMapper objectMapper,
      KubernetesResourcePropertyRegistry registry,
      KubernetesV2ArtifactProvider artifactProvider) {
    this.setAccountCredentialsProvider(accountCredentialsProvider);
    this.setObjectMapper(objectMapper);
    this.registry = registry;
    this.artifactProvider = artifactProvider;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new KubernetesDeployManifestOperation(
        convertDescription(input), registry, artifactProvider);
  }

  @Override
  public KubernetesDeployManifestDescription convertDescription(Map input) {
    KubernetesDeployManifestDescription mainDescription =
        (KubernetesDeployManifestDescription)
            KubernetesAtomicOperationConverterHelper.convertDescription(
                input, this, KubernetesDeployManifestDescription.class);
    return convertListDescription(mainDescription);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }

  /**
   * If present, converts a KubernetesManifest of kind List into a list of KubernetesManifest
   * objects.
   *
   * @param mainDescription deploy manifest description as received.
   * @return updated description.
   */
  @SuppressWarnings("unchecked")
  private KubernetesDeployManifestDescription convertListDescription(
      KubernetesDeployManifestDescription mainDescription) {

    if (mainDescription.getManifests() == null) {
      return mainDescription;
    }

    List<KubernetesManifest> updatedManifestList =
        mainDescription.getManifests().stream()
            .flatMap(
                singleManifest -> {
                  if (singleManifest == null
                      || StringUtils.isEmpty(singleManifest.getKindName())
                      || !singleManifest.getKindName().equalsIgnoreCase(KIND_VALUE_LIST)) {
                    return Stream.of(singleManifest);
                  }

                  Collection<Object> items =
                      (Collection<Object>) singleManifest.get(KIND_LIST_ITEMS_KEY);

                  if (items == null) {
                    return Stream.of();
                  }

                  return items.stream()
                      .map(i -> getObjectMapper().convertValue(i, KubernetesManifest.class));
                })
            .collect(Collectors.toList());

    mainDescription.setManifests(updatedManifestList);

    return mainDescription;
  }
}
