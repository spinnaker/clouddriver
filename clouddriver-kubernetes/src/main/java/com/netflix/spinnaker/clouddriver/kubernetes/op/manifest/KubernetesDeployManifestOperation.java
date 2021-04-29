/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.manifest;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.*;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy.Versioned;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CanLoadBalance;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.CanScale;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class KubernetesDeployManifestOperation implements AtomicOperation<OperationResult> {
  private static final Logger log =
      LoggerFactory.getLogger(KubernetesDeployManifestOperation.class);
  private final KubernetesDeployManifestDescription description;
  private final KubernetesCredentials credentials;
  private final ResourceVersioner resourceVersioner;
  @Nonnull private final String accountName;
  private static final String OP_NAME = "DEPLOY_KUBERNETES_MANIFEST";

  public KubernetesDeployManifestOperation(
      KubernetesDeployManifestDescription description, ResourceVersioner resourceVersioner) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
    this.resourceVersioner = resourceVersioner;
    this.accountName = description.getCredentials().getName();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List<OperationResult> _unused) {
    getTask().updateStatus(OP_NAME, "Beginning deployment of manifest...");

    List<KubernetesManifest> inputManifests = description.getManifests();
    if (inputManifests == null || inputManifests.isEmpty()) {
      // The stage currently only supports using the `manifests` field but we need to continue to
      // check `manifest` for backwards compatibility until all existing stages have been updated.
      @SuppressWarnings("deprecation")
      KubernetesManifest manifest = description.getManifest();
      log.warn(
          "Relying on deprecated single manifest input (account: {}, kind: {}, name: {})",
          accountName,
          manifest.getKind(),
          manifest.getName());
      inputManifests = ImmutableList.of(manifest);
    }

    inputManifests = inputManifests.stream().filter(Objects::nonNull).collect(Collectors.toList());

    OperationResult result = new OperationResult();
    List<ManifestArtifactHolder> deployManifests = bindArtifacts(inputManifests, result);
    sortManifests(deployManifests);

    for (ManifestArtifactHolder holder : deployManifests) {
      KubernetesManifest manifest = holder.manifest;
      Artifact artifact = holder.artifact;

      getTask()
          .updateStatus(
              OP_NAME,
              "Annotating manifest "
                  + manifest.getFullResourceName()
                  + " with artifact, relationships & moniker...");
      KubernetesManifestAnnotater.annotateManifest(manifest, artifact);

      KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);
      KubernetesHandler deployer = findResourceProperties(manifest).getHandler();
      if (strategy.isUseSourceCapacity() && deployer instanceof CanScale) {
        Double replicas = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials);
        if (replicas != null) {
          manifest.setReplicas(replicas);
        }
      }

      setTrafficAnnotation(description.getServices(), manifest);
      if (description.isEnableTraffic()) {
        KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
        applyTraffic(
            traffic,
            manifest,
            deployManifests.stream().map(h -> h.manifest).collect(Collectors.toList()));
      }

      Moniker moniker = cloneMoniker(description.getMoniker());
      Optional<Integer> version = versionFromArtifact(artifact);
      version.ifPresent(moniker::setSequence);
      if (Strings.isNullOrEmpty(moniker.getCluster())) {
        moniker.setCluster(manifest.getFullResourceName());
      }
      credentials.getNamer().applyMoniker(manifest, moniker);
      manifest.setName(artifact.getReference());

      getTask()
          .updateStatus(
              OP_NAME,
              "Submitting manifest " + manifest.getFullResourceName() + " to kubernetes master...");
      log.debug("Manifest in {} to be deployed: {}", accountName, manifest);
      result.merge(deployer.deploy(credentials, manifest, strategy.getDeployStrategy()));

      result.getCreatedArtifacts().add(artifact);
    }

    result.removeSensitiveKeys(credentials.getResourcePropertyRegistry());

    getTask().updateStatus(OP_NAME, "Deploy manifest task completed successfully.");
    return result;
  }

  /**
   * Modifies input manifests by binding artifacts in this order: 1. Binds artifacts created from
   * the same input manifests list (example: in a list with a Deployment and ConfigMap, the
   * ConfigMap is bound to the Deployment) 2. Binds required artifacts specified in the stage
   * definition 3. Binds optional artifacts present in the pipeline context
   *
   * @param inputManifests list of manifests to bind
   * @param operationResult object for storing bounded manifests
   * @return list of holders having the modified input manifest and the artifact representing that
   *     manifest
   */
  private List<ManifestArtifactHolder> bindArtifacts(
      List<KubernetesManifest> inputManifests, OperationResult operationResult) {

    // Get the artifact representing each manifest
    List<ManifestArtifactHolder> manifestArtifacts = new ArrayList<>();
    for (KubernetesManifest manifest : inputManifests) {
      KubernetesResourceProperties properties = findResourceProperties(manifest);
      KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);

      OptionalInt version =
          isVersioned(properties, strategy)
              ? resourceVersioner.getVersion(manifest, credentials)
              : OptionalInt.empty();

      manifestArtifacts.add(
          new ManifestArtifactHolder(
              manifest, ArtifactConverter.toArtifact(manifest, description.getAccount(), version)));
    }

    Map<String, Artifact> allArtifacts = new HashMap<>();
    // Order is important, it determines priority of artifact binding.
    // The first one for the same key wins. Manifest artifacts are used
    // when there are multiple manifests in the same stage depending on each other
    manifestArtifacts.forEach(
        (ma) -> allArtifacts.putIfAbsent(getArtifactKey(ma.artifact), ma.artifact));
    if (description.isEnableArtifactBinding()) {
      // Required artifacts are explicitly set in stage configuration
      if (description.getRequiredArtifacts() != null) {
        description
            .getRequiredArtifacts()
            .forEach(a -> allArtifacts.putIfAbsent(getArtifactKey(a), a));
      }
      // Optional artifacts are taken from the pipeline trigger or pipeline execution context
      if (description.getOptionalArtifacts() != null) {
        description
            .getOptionalArtifacts()
            .forEach(a -> allArtifacts.putIfAbsent(getArtifactKey(a), a));
      }
    }

    manifestArtifacts =
        manifestArtifacts.stream()
            .map(
                ma -> {
                  KubernetesManifestAnnotater.validateAnnotationsForRolloutStrategies(
                      ma.manifest, description.getStrategy());

                  getTask()
                      .updateStatus(
                          OP_NAME,
                          "Binding artifacts in " + ma.manifest.getFullResourceName() + "...");

                  ReplaceResult replaceResult =
                      findResourceProperties(ma.manifest)
                          .getHandler()
                          .replaceArtifacts(
                              ma.manifest,
                              List.copyOf(allArtifacts.values()),
                              description.getAccount());

                  getTask()
                      .updateStatus(
                          OP_NAME, "Bound artifacts: " + replaceResult.getBoundArtifacts() + "...");

                  operationResult.getBoundArtifacts().addAll(replaceResult.getBoundArtifacts());
                  return new ManifestArtifactHolder(replaceResult.getManifest(), ma.artifact);
                })
            .collect(Collectors.toList());

    Set<ArtifactKey> unboundArtifacts =
        Sets.difference(
            ArtifactKey.fromArtifacts(description.getRequiredArtifacts()),
            ArtifactKey.fromArtifacts(operationResult.getBoundArtifacts()));

    getTask().updateStatus(OP_NAME, "Checking if all requested artifacts were bound...");
    if (description.isEnableArtifactBinding() && !unboundArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The following required artifacts could not be bound: '%s'. "
                  + "Check that the Docker image name above matches the name used in the image field of your manifest. "
                  + "Failing the stage as this is likely a configuration error.",
              unboundArtifacts));
    }

    return manifestArtifacts;
  }

  private void sortManifests(List<ManifestArtifactHolder> holders) {
    getTask().updateStatus(OP_NAME, "Sorting manifests by priority...");
    holders.sort(
        Comparator.comparingInt(
            h -> findResourceProperties(h.manifest).getHandler().deployPriority()));
    getTask()
        .updateStatus(
            OP_NAME,
            "Deploy order is: "
                + holders.stream()
                    .map(h -> h.manifest.getFullResourceName())
                    .collect(Collectors.joining(", ")));
  }

  private Optional<Integer> versionFromArtifact(Artifact artifact) {
    if (StringUtils.isEmpty(artifact.getVersion())) {
      return Optional.empty();
    }
    String versionString;
    if (artifact.getVersion().startsWith("v")) {
      versionString = artifact.getVersion().substring(1);
    } else {
      versionString = artifact.getVersion();
    }
    try {
      return Optional.of(Integer.parseInt(versionString));
    } catch (NumberFormatException e) {
      log.warn("Cannot convert version string \"" + versionString + "\" to integer");
      return Optional.empty();
    }
  }

  private String getArtifactKey(Artifact artifact) {
    return String.format("[%s]-[%s]", artifact.getType(), artifact.getName());
  }

  private void setTrafficAnnotation(List<String> services, KubernetesManifest manifest) {
    if (services == null || services.isEmpty()) {
      return;
    }
    KubernetesManifestTraffic traffic = new KubernetesManifestTraffic(services);
    KubernetesManifestAnnotater.setTraffic(manifest, traffic);
  }

  private void applyTraffic(
      KubernetesManifestTraffic traffic,
      KubernetesManifest target,
      Collection<KubernetesManifest> manifestsFromRequest) {
    traffic.getLoadBalancers().forEach(l -> attachLoadBalancer(l, target, manifestsFromRequest));
  }

  private void attachLoadBalancer(
      String loadBalancerName,
      KubernetesManifest target,
      Collection<KubernetesManifest> manifestsFromRequest) {

    KubernetesCoordinates coords;
    try {
      coords =
          KubernetesCoordinates.builder()
              .namespace(target.getNamespace())
              .fullResourceName(loadBalancerName)
              .build();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to attach load balancer '%s'. Load balancers must be specified in the form '{kind} {name}', e.g. 'service my-service'.",
              loadBalancerName),
          e);
    }

    KubernetesManifest loadBalancer = getLoadBalancer(coords, manifestsFromRequest);

    CanLoadBalance handler =
        CanLoadBalance.lookupProperties(credentials.getResourcePropertyRegistry(), coords);

    getTask()
        .updateStatus(
            OP_NAME,
            "Attaching load balancer "
                + loadBalancer.getFullResourceName()
                + " to "
                + target.getFullResourceName());

    handler.attach(loadBalancer, target);
  }

  private KubernetesManifest getLoadBalancer(
      KubernetesCoordinates coords, Collection<KubernetesManifest> manifestsFromRequest) {
    Optional<KubernetesManifest> loadBalancer =
        manifestsFromRequest.stream()
            .filter(m -> KubernetesCoordinates.fromManifest(m).equals(coords))
            .findFirst();

    return loadBalancer.orElseGet(
        () ->
            Optional.ofNullable(credentials.get(coords))
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Load balancer "
                                + coords.getKind().toString()
                                + " "
                                + coords.getName()
                                + " does not exist")));
  }

  private boolean isVersioned(
      KubernetesResourceProperties properties, KubernetesManifestStrategy strategy) {
    if (strategy.getVersioned() != Versioned.DEFAULT) {
      return strategy.getVersioned() == Versioned.TRUE;
    }

    if (description.getVersioned() != null) {
      return description.getVersioned();
    }

    return properties.isVersioned();
  }

  // todo(lwander): move to kork
  private static Moniker cloneMoniker(Moniker inp) {
    return Moniker.builder()
        .app(inp.getApp())
        .cluster(inp.getCluster())
        .stack(inp.getStack())
        .detail(inp.getDetail())
        .sequence(inp.getSequence())
        .build();
  }

  @Nonnull
  private KubernetesResourceProperties findResourceProperties(KubernetesManifest manifest) {
    KubernetesKind kind = manifest.getKind();
    getTask().updateStatus(OP_NAME, "Finding deployer for " + kind + "...");
    return credentials.getResourcePropertyRegistry().get(kind);
  }

  @Data
  private static class ManifestArtifactHolder {
    private final KubernetesManifest manifest;
    private final Artifact artifact;
  }
}
