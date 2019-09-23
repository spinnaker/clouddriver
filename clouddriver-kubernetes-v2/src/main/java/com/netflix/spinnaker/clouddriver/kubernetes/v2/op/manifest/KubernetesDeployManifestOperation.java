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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.ReplaceResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.KubernetesArtifactConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.*;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CanLoadBalance;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CanScale;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class KubernetesDeployManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesDeployManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final ArtifactProvider provider;
  private final Namer<KubernetesManifest> namer;
  private final String accountName;
  private static final String OP_NAME = "DEPLOY_KUBERNETES_MANIFEST";

  public KubernetesDeployManifestOperation(
      KubernetesDeployManifestDescription description, ArtifactProvider provider) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.provider = provider;
    this.accountName = description.getCredentials().getName();
    this.namer =
        NamerRegistry.lookup()
            .withProvider(KubernetesCloudProvider.ID)
            .withAccount(accountName)
            .withResource(KubernetesManifest.class);
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List _unused) {
    getTask().updateStatus(OP_NAME, "Beginning deployment of manifest...");

    List<KubernetesManifest> inputManifests = description.getManifests();
    List<KubernetesManifest> deployManifests = new ArrayList<>();
    if (inputManifests == null || inputManifests.isEmpty()) {
      log.warn("Relying on deprecated single manifest input: " + description.getManifest());
      inputManifests = Collections.singletonList(description.getManifest());
    }

    inputManifests = inputManifests.stream().filter(Objects::nonNull).collect(Collectors.toList());

    List<Artifact> requiredArtifacts = description.getRequiredArtifacts();
    if (requiredArtifacts == null) {
      requiredArtifacts = new ArrayList<>();
    }

    List<Artifact> optionalArtifacts = description.getOptionalArtifacts();
    if (optionalArtifacts == null) {
      optionalArtifacts = new ArrayList<>();
    }

    List<Artifact> artifacts = new ArrayList<>();
    artifacts.addAll(requiredArtifacts);
    artifacts.addAll(optionalArtifacts);

    Set<Artifact> boundArtifacts = new HashSet<>();

    validateManifestsForRolloutStrategies(inputManifests);

    for (KubernetesManifest manifest : inputManifests) {
      if (credentials.getKindRegistry().getKindProperties(manifest.getKind()).isNamespaced()) {
        if (!StringUtils.isEmpty(description.getNamespaceOverride())) {
          manifest.setNamespace(description.getNamespaceOverride());
        } else if (StringUtils.isEmpty(manifest.getNamespace())) {
          manifest.setNamespace(credentials.getDefaultNamespace());
        }
      }

      KubernetesResourceProperties properties = findResourceProperties(manifest);
      KubernetesHandler deployer = properties.getHandler();
      if (deployer == null) {
        throw new IllegalArgumentException(
            "No deployer available for Kubernetes object kind '"
                + manifest.getKind().toString()
                + "', unable to continue.");
      }

      KubernetesManifestAnnotater.validateAnnotationsForRolloutStrategies(
          manifest, description.getStrategy());

      getTask()
          .updateStatus(
              OP_NAME,
              "Swapping out artifacts in " + manifest.getFullResourceName() + " from context...");
      ReplaceResult replaceResult =
          deployer.replaceArtifacts(manifest, artifacts, description.getAccount());
      deployManifests.add(replaceResult.getManifest());
      boundArtifacts.addAll(replaceResult.getBoundArtifacts());
    }

    Set<Artifact> unboundArtifacts = new HashSet<>(requiredArtifacts);
    unboundArtifacts.removeAll(boundArtifacts);

    getTask().updateStatus(OP_NAME, "Checking if all requested artifacts were bound...");
    if (!unboundArtifacts.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The following required artifacts could not be bound: '%s'."
                  + "Check that the Docker image name above matches the name used in the image field of your manifest."
                  + "Failing the stage as this is likely a configuration error.",
              unboundArtifacts));
    }

    getTask().updateStatus(OP_NAME, "Sorting manifests by priority...");
    deployManifests.sort(
        Comparator.comparingInt(m -> findResourceProperties(m).getHandler().deployPriority()));
    getTask()
        .updateStatus(
            OP_NAME,
            "Deploy order is: "
                + deployManifests.stream()
                    .map(KubernetesManifest::getFullResourceName)
                    .collect(Collectors.joining(", ")));

    OperationResult result = new OperationResult();
    for (KubernetesManifest manifest : deployManifests) {
      KubernetesResourceProperties properties = findResourceProperties(manifest);
      KubernetesManifestStrategy strategy = KubernetesManifestAnnotater.getStrategy(manifest);
      boolean versioned = isVersioned(properties, strategy);
      boolean useSourceCapacity = isUseSourceCapacity(strategy);
      boolean recreate = isRecreate(strategy);
      boolean replace = isReplace(strategy);

      KubernetesArtifactConverter converter =
          versioned ? properties.getVersionedConverter() : properties.getUnversionedConverter();
      KubernetesHandler deployer = properties.getHandler();

      Moniker moniker = cloneMoniker(description.getMoniker());
      if (StringUtils.isEmpty(moniker.getCluster())) {
        moniker.setCluster(manifest.getFullResourceName());
      }

      Artifact artifact = converter.toArtifact(provider, manifest, description.getAccount());

      String version = artifact.getVersion();
      if (StringUtils.isNotEmpty(version) && version.startsWith("v")) {
        try {
          moniker.setSequence(Integer.valueOf(version.substring(1)));
        } catch (NumberFormatException e) {
          log.warn("Malformed moniker version {}", version, e);
        }
      }

      getTask()
          .updateStatus(
              OP_NAME,
              "Annotating manifest "
                  + manifest.getFullResourceName()
                  + " with artifact, relationships & moniker...");
      KubernetesManifestAnnotater.annotateManifest(manifest, artifact);

      if (useSourceCapacity && deployer instanceof CanScale) {
        Double replicas = KubernetesSourceCapacity.getSourceCapacity(manifest, credentials);
        if (replicas != null) {
          manifest.setReplicas(replicas);
        }
      }

      setTrafficAnnotation(description.getServices(), manifest);
      if (description.isEnableTraffic()) {
        KubernetesManifestTraffic traffic = KubernetesManifestAnnotater.getTraffic(manifest);
        applyTraffic(traffic, manifest);
      }

      namer.applyMoniker(manifest, moniker);
      manifest.setName(converter.getDeployedName(artifact));

      getTask()
          .updateStatus(
              OP_NAME,
              "Swapping out artifacts in "
                  + manifest.getFullResourceName()
                  + " from other deployments...");
      ReplaceResult replaceResult =
          deployer.replaceArtifacts(
              manifest, new ArrayList<>(result.getCreatedArtifacts()), description.getAccount());
      boundArtifacts.addAll(replaceResult.getBoundArtifacts());
      manifest = replaceResult.getManifest();

      getTask()
          .updateStatus(
              OP_NAME,
              "Submitting manifest " + manifest.getFullResourceName() + " to kubernetes master...");
      log.debug("Manifest in {} to be deployed: {}", accountName, manifest);
      result.merge(deployer.deploy(credentials, manifest, recreate, replace));

      result.getCreatedArtifacts().add(artifact);
    }

    result.getBoundArtifacts().addAll(boundArtifacts);
    result.removeSensitiveKeys(credentials.getResourcePropertyRegistry());

    getTask().updateStatus(OP_NAME, "Deploy manifest task completed successfully.");
    return result;
  }

  private void setTrafficAnnotation(List<String> services, KubernetesManifest manifest) {
    if (services == null || services.isEmpty()) {
      return;
    }
    KubernetesManifestTraffic traffic = new KubernetesManifestTraffic(services);
    KubernetesManifestAnnotater.setTraffic(manifest, traffic);
  }

  private void applyTraffic(KubernetesManifestTraffic traffic, KubernetesManifest target) {
    traffic.getLoadBalancers().forEach(l -> attachLoadBalancer(l, target));
  }

  private void validateManifestsForRolloutStrategies(List<KubernetesManifest> manifests) {
    long numReplicaSets =
        manifests.stream().filter(m -> m.getKind().equals(KubernetesKind.REPLICA_SET)).count();
    if (description.getStrategy() != null && numReplicaSets != 1) {
      throw new RuntimeException(
          "Spinnaker can manage traffic for one ReplicaSet only. Please deploy one ReplicaSet manifest or disable rollout strategies.");
    }
  }

  private void attachLoadBalancer(String loadBalancerName, KubernetesManifest target) {
    Pair<KubernetesKind, String> name;
    try {
      name = KubernetesManifest.fromFullResourceName(loadBalancerName);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to attach load balancer '%s'. Load balancers must be specified in the form '{kind} {name}', e.g. 'service my-service'.",
              loadBalancerName),
          e);
    }

    CanLoadBalance handler =
        CanLoadBalance.lookupProperties(credentials.getResourcePropertyRegistry(), name);

    // TODO(lwander): look into using a combination of the cache & other resources passed in with
    // this request instead of making a live call here.
    KubernetesManifest loadBalancer =
        credentials.get(name.getLeft(), target.getNamespace(), name.getRight());
    if (loadBalancer == null) {
      throw new IllegalArgumentException("Load balancer " + loadBalancerName + " does not exist");
    }

    getTask()
        .updateStatus(
            OP_NAME,
            "Attaching load balancer "
                + loadBalancer.getFullResourceName()
                + " to "
                + target.getFullResourceName());

    handler.attach(loadBalancer, target);
  }

  private boolean isVersioned(
      KubernetesResourceProperties properties, KubernetesManifestStrategy strategy) {
    if (strategy.getVersioned() != null) {
      return strategy.getVersioned();
    }

    if (description.getVersioned() != null) {
      return description.getVersioned();
    }

    return properties.isVersioned();
  }

  private boolean isRecreate(KubernetesManifestStrategy strategy) {
    return strategy.getRecreate() != null ? strategy.getRecreate() : false;
  }

  private boolean isReplace(KubernetesManifestStrategy strategy) {
    return strategy.getReplace() != null ? strategy.getReplace() : false;
  }

  private boolean isUseSourceCapacity(KubernetesManifestStrategy strategy) {
    if (strategy.getUseSourceCapacity() != null) {
      return strategy.getUseSourceCapacity();
    }

    return false;
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
}
