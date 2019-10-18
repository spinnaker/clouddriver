/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions.MergeStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesPatchManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class KubernetesPatchManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesPatchManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private static final String OP_NAME = "PATCH_KUBERNETES_MANIFEST";

  @Autowired private ObjectMapper objectMapper;

  public KubernetesPatchManifestOperation(KubernetesPatchManifestDescription description) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List _unused) {
    updateStatus("Beginning patching of manifest");
    KubernetesCoordinates objToPatch = description.getPointCoordinates();

    updateStatus("Finding patch handler for " + objToPatch + "...");
    KubernetesHandler patchHandler = findPatchHandler(objToPatch);

    OperationResult result = new OperationResult();

    MergeStrategy mergeStrategy = description.getOptions().getMergeStrategy();

    if (mergeStrategy == MergeStrategy.json) {
      // Skip artifact replacement for json patches
      updateStatus(
          "Submitting manifest " + description.getManifestName() + " to Kubernetes master...");
      List<JsonPatch> jsonPatches =
          objectMapper.convertValue(
              description.getPatchBody(), new TypeReference<List<JsonPatch>>() {});
      result.merge(
          patchHandler.patchWithJson(
              credentials,
              objToPatch.getNamespace(),
              objToPatch.getName(),
              description.getOptions(),
              jsonPatches));
    } else {
      updateStatus("Swapping out artifacts in " + objToPatch + " from context...");
      ReplaceResult replaceResult = replaceArtifacts(objToPatch, patchHandler);

      updateStatus(
          "Submitting manifest " + description.getManifestName() + " to Kubernetes master...");
      result.merge(
          patchHandler.patchWithManifest(
              credentials,
              objToPatch.getNamespace(),
              objToPatch.getName(),
              description.getOptions(),
              replaceResult.getManifest()));
      result.getBoundArtifacts().addAll(replaceResult.getBoundArtifacts());
    }

    result.removeSensitiveKeys(credentials.getResourcePropertyRegistry());
    return result;
  }

  private void updateStatus(String status) {
    getTask().updateStatus(OP_NAME, status);
  }

  private ReplaceResult replaceArtifacts(
      KubernetesCoordinates objToPatch, KubernetesHandler patchHandler) {
    List<Artifact> allArtifacts =
        description.getAllArtifacts() == null ? new ArrayList<>() : description.getAllArtifacts();

    KubernetesManifest manifest =
        objectMapper.convertValue(description.getPatchBody(), KubernetesManifest.class);
    ReplaceResult replaceResult =
        patchHandler.replaceArtifacts(
            manifest, allArtifacts, objToPatch.getNamespace(), description.getAccount());

    if (description.getRequiredArtifacts() != null) {
      Set<ArtifactKey> unboundArtifacts =
          Sets.difference(
              ArtifactKey.fromArtifacts(description.getRequiredArtifacts()),
              ArtifactKey.fromArtifacts(replaceResult.getBoundArtifacts()));
      if (!unboundArtifacts.isEmpty()) {
        throw new IllegalArgumentException(
            String.format(
                "The following required artifacts could not be bound: '%s'."
                    + "Check that the Docker image name above matches the name used in the image field of your manifest."
                    + "Failing the stage as this is likely a configuration error.",
                unboundArtifacts));
      }
    }
    return replaceResult;
  }

  private KubernetesHandler findPatchHandler(KubernetesCoordinates objToPatch) {
    KubernetesResourceProperties properties =
        credentials.getResourcePropertyRegistry().get(objToPatch.getKind());
    KubernetesHandler patchHandler = properties.getHandler();
    if (patchHandler == null) {
      throw new IllegalArgumentException(
          "No patch handler available for Kubernetes object kind ' "
              + objToPatch.getKind()
              + "', unable to continue");
    }
    return patchHandler;
  }
}
