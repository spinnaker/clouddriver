/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscalerBuilder;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class ArtifactReplacerTest {
  // We serialized generated Kubernetes metadata objects with JSON io.kubernetes.client.openapi.JSON
  // so that they match what we get back from kubectl.  We'll just gson from converting to a
  // KubernetesManifest because that's what we currently use to parse the result from kubectl and
  // we want this test to be realistic.
  private static final JSON json = new JSON();
  private static final Gson gson = new Gson();

  @Test
  void extractsDeploymentNameFromHpa() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaDeployment()));
    KubernetesManifest hpa = getHpa("Deployment", "my-deployment");
    Set<Artifact> artifacts = artifactReplacer.findAll(hpa);

    assertThat(artifacts).hasSize(1);
    Artifact artifact = Iterables.getOnlyElement(artifacts);
    assertThat(artifact.getName()).isEqualTo("my-deployment");
    assertThat(artifact.getType()).isEqualTo(KubernetesArtifactType.Deployment.getType());
  }

  @Test
  void skipsHpaWithUnknownKind() {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.hpaDeployment()));
    KubernetesManifest hpa = getHpa("Unknown", "my-deployment");
    Set<Artifact> artifacts = artifactReplacer.findAll(hpa);

    assertThat(artifacts).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("imageArtifactTestCases")
  void extractsDockerImageArtifacts(ImageTestCase testCase) {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.dockerImage()));
    KubernetesManifest deployment = getDeploymentWithContainer(getContainer(testCase.getImage()));
    Set<Artifact> artifacts = artifactReplacer.findAll(deployment);

    assertThat(artifacts).hasSize(1);
    Artifact artifact = Iterables.getOnlyElement(artifacts);
    assertThat(artifact.getType()).isEqualTo(KubernetesArtifactType.DockerImage.getType());
    assertThat(artifact.getName()).isEqualTo(testCase.getName());
    assertThat(artifact.getReference()).isEqualTo(testCase.getImage());
  }

  @ParameterizedTest
  @MethodSource("imageArtifactTestCases")
  void extractsDockerImageArtifactsFromInitContainers(ImageTestCase testCase) {
    ArtifactReplacer artifactReplacer =
        new ArtifactReplacer(ImmutableList.of(Replacer.dockerImage()));
    KubernetesManifest deployment =
        getDeploymentWithInitContainer(getContainer(testCase.getImage()));
    Set<Artifact> artifacts = artifactReplacer.findAll(deployment);

    assertThat(artifacts).hasSize(1);
    Artifact artifact = Iterables.getOnlyElement(artifacts);
    assertThat(artifact.getType()).isEqualTo(KubernetesArtifactType.DockerImage.getType());
    assertThat(artifact.getName()).isEqualTo(testCase.getName());
    assertThat(artifact.getReference()).isEqualTo(testCase.getImage());
  }

  private static Stream<ImageTestCase> imageArtifactTestCases() {
    return Stream.of(
        ImageTestCase.of("nginx:112", "nginx"),
        ImageTestCase.of("nginx:1.12-alpine", "nginx"),
        ImageTestCase.of("my-nginx:100000", "my-nginx"),
        ImageTestCase.of("my.nginx:100000", "my.nginx"),
        ImageTestCase.of("reg/repo:1.2.3", "reg/repo"),
        ImageTestCase.of("reg.repo:123@sha256:13", "reg.repo:123"),
        ImageTestCase.of("reg.default.svc/r/j:485fabc", "reg.default.svc/r/j"),
        ImageTestCase.of("reg:5000/r/j:485fabc", "reg:5000/r/j"),
        ImageTestCase.of("reg:5000/r__j:485fabc", "reg:5000/r__j"),
        ImageTestCase.of("clouddriver", "clouddriver"),
        ImageTestCase.of("clouddriver@sha256:9145", "clouddriver"),
        ImageTestCase.of(
            "localhost:5000/test/busybox@sha256:cbbf22", "localhost:5000/test/busybox"));
  }

  @RequiredArgsConstructor
  @Value
  private static class ImageTestCase {
    final String image;
    final String name;

    static ImageTestCase of(String image, String name) {
      return new ImageTestCase(image, name);
    }
  }

  private KubernetesManifest getHpa(String kind, String name) {
    String hpa =
        json.serialize(
            new V1HorizontalPodAutoscalerBuilder()
                .withNewMetadata()
                .withName("my-hpa")
                .withNamespace("default")
                .endMetadata()
                .withNewSpec()
                .withNewScaleTargetRef()
                .withApiVersion("apps/v1")
                .withKind(kind)
                .withName(name)
                .endScaleTargetRef()
                .endSpec()
                .build());
    return gson.fromJson(hpa, KubernetesManifest.class);
  }

  private V1Container getContainer(String image) {
    return new V1ContainerBuilder()
        .withName("container")
        .withImage(image)
        .addNewPort()
        .withContainerPort(80)
        .endPort()
        .build();
  }

  private KubernetesManifest getDeploymentWithContainer(V1Container container) {
    return getDeployment(ImmutableList.of(container), ImmutableList.of());
  }

  private KubernetesManifest getDeploymentWithInitContainer(V1Container container) {
    return getDeployment(ImmutableList.of(), ImmutableList.of(container));
  }

  private KubernetesManifest getDeployment(
      Collection<V1Container> containers, Collection<V1Container> initContainers) {
    String deployment =
        json.serialize(
            new V1DeploymentBuilder()
                .withNewMetadata()
                .withName("my-app-deployment")
                .withLabels(ImmutableMap.of("app", "my-app"))
                .endMetadata()
                .withNewSpec()
                .withReplicas(3)
                .withNewSelector()
                .withMatchLabels(ImmutableMap.of("app", "my-app"))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(ImmutableMap.of("app", "my-app"))
                .endMetadata()
                .withNewSpec()
                .addAllToContainers(containers)
                .addAllToInitContainers(initContainers)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
    return gson.fromJson(deployment, KubernetesManifest.class);
  }
}
