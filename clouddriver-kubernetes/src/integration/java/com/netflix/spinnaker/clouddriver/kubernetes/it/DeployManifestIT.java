/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DeployManifestIT extends BaseTest {

  @DisplayName(
      ".\n===\n"
          + "Given a nginx deployment manifest with no namespace set\n"
          + "  And a namespace override\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx pod is up and running in the overridden namespace\n===")
  @Test
  public void shouldDeployManifestFromText() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml").asList();
    String overrideNamespace = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + overrideNamespace);

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", overrideNamespace)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, overrideNamespace, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + overrideNamespace + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + overrideNamespace
                + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a nginx deployment manifest with no namespace set\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx pod is up and running in the default namespace\n===")
  @Test
  public void shouldDeployManifestToDefaultNs() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml").asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, "default", "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n default get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n default" + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a document with multiple manifest definitions\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx service and pod exist in the target cluster\n===")
  @Test
  public void shouldDeployMultidocManifest() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/multi_nginx.yml")
            .withValue("metadata.namespace", ns)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx", "service nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String services = kubeCluster.execKubectl("-n " + ns + " get services");
    assertTrue(
        Strings.isNotEmpty(kubeCluster.execKubectl("-n " + ns + " get services nginx")),
        "Expected service nginx to exist. Services: " + services);
  }

  @DisplayName(
      ".\n===\n"
          + "Given nginx deployed with spinnaker\n"
          + "  And nginx manifest updated with a new tag version\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then old version is deleted and new version is available\n===")
  @Test
  public void shouldUpdateExistingDeployment() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String oldImage = "index.docker.io/library/nginx:1.14.0";
    String newImage = "index.docker.io/library/nginx:1.15.0";

    List<Map<String, Object>> oldManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", oldImage)
            .asList();

    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", oldManifest)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");
    String currentImage =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(oldImage, currentImage, "Expected correct nginx image to be deployed");

    List<Map<String, Object>> newManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", newImage)
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", newManifest)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    currentImage =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(newImage, currentImage, "Expected correct nginx image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given nginx manifest without image tag\n"
          + "  And optional nginx docker artifact present\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx artifact is deployed\n===")
  @Test
  public void shouldBindOptionalDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String imageNoTag = "index.docker.io/library/nginx";
    String imageWithTag = "index.docker.io/library/nginx:1.15.0";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", imageWithTag)
            .withValue("version", imageWithTag.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(imageWithTag, imageDeployed, "Expected correct nginx image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given nginx manifest without image tag\n"
          + "  And required nginx docker artifact present\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx artifact is deployed\n===")
  @Test
  public void shouldBindRequiredDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String imageNoTag = "index.docker.io/library/nginx";
    String imageWithTag = "index.docker.io/library/nginx:1.15.0";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", imageWithTag)
            .withValue("version", imageWithTag.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", artifact)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(imageWithTag, imageDeployed, "Expected correct nginx image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given nginx manifest without image tag\n"
          + "  And required nginx docker artifact present\n"
          + "  And optional nginx docker artifact present\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then required nginx artifact is deployed\n===")
  @Test
  public void shouldBindRequiredOverOptionalDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String imageNoTag = "index.docker.io/library/nginx";
    String requiredImage = "index.docker.io/library/nginx:1.16.0";
    String optionalImage = "index.docker.io/library/nginx:1.15.0";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asList();
    Map<String, Object> requiredArtifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", requiredImage)
            .withValue("version", requiredImage.substring(imageNoTag.length() + 1))
            .asMap();
    Map<String, Object> optionalArtifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", optionalImage)
            .withValue("version", optionalImage.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", requiredArtifact)
            .withValue("deployManifest.optionalArtifacts[0]", optionalArtifact)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(requiredImage, imageDeployed, "Expected correct nginx image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given nginx manifest referencing an unversioned configmap\n"
          + "  And versioned configmap deployed\n"
          + "  And versioned configmap artifact\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx is deployed mounting versioned configmap\n===")
  @Test
  public void shouldBindVersionedConfigMap() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";
    String version = "v005";

    // deploy versioned configmap
    Map<String, Object> cm =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName + "-" + version)
            .asMap();
    kubeCluster.execKubectl("-n " + ns + " apply -f -", cm);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx_vol.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.volumes[0].configMap.name", cmName)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", cmName)
            .withValue("type", "kubernetes/configMap")
            .withValue("reference", cmName + "-" + version)
            .withValue("location", ns)
            .withValue("version", version)
            .withValue("metadata.account", ACCOUNT1_NAME)
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String cmNameDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.volumes[0].configMap.name}'");
    assertEquals(
        cmName + "-" + version, cmNameDeployed, "Expected correct configmap to be referenced");
  }

  @DisplayName(
      ".\n===\n"
          + "Given nginx manifest referencing an unversioned secret\n"
          + "  And versioned secret deployed\n"
          + "  And versioned secret artifact\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx is deployed mounting versioned secret\n===")
  @Test
  public void shouldBindVersionedSecret() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";
    String version = "v009";

    // deploy versioned secret
    Map<String, Object> secret =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName + "-" + version)
            .asMap();
    kubeCluster.execKubectl("-n " + ns + " apply -f -", secret);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_nginx_vol.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.volumes[0].secret.secretName", secretName)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", secretName)
            .withValue("type", "kubernetes/secret")
            .withValue("reference", secretName + "-" + version)
            .withValue("location", ns)
            .withValue("version", version)
            .withValue("metadata.account", ACCOUNT1_NAME)
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    deployAndWaitStable(body, ns, "deployment nginx");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String secretNameDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment nginx -o=jsonpath='{.spec.template.spec.volumes[0].secret.secretName}'");
    assertEquals(
        secretName + "-" + version, secretNameDeployed, "Expected correct secret to be referenced");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a configmap manifest\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then configmap is deployed with a version suffix name\n===")
  @Test
  public void shouldAddVersionToConfigmap() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "configMap " + cmName + "-v000");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName + "-v000");
    assertTrue(cm.contains("v000"), "Expected configmap with name " + cmName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret manifest\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then secret is deployed with a version suffix name\n===")
  @Test
  public void shouldAddVersionToSecret() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "secret " + secretName + "-v000");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName + "-v000");
    assertTrue(cm.contains("v000"), "Expected secret with name " + secretName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a configmap deployed with spinnaker\n"
          + "  And configmap manifest changed\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then a new version of configmap is deployed\n"
          + "  And the previous version of configmap is not deleted or changed\n===")
  @Test
  public void shouldDeployNewConfigmapVersion() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "configMap " + cmName + "-v000");

    manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .withValue("data.newfile", "new content")
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "configMap " + cmName + "-v001");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName + "-v001");
    assertTrue(cm.contains("v001"), "Expected configmap with name " + cmName + "-v001");
    cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName + "-v000");
    assertTrue(cm.contains("v000"), "Expected configmap with name " + cmName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret deployed with spinnaker\n"
          + "  And secret manifest changed\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then a new version of secret is deployed\n"
          + "  And the previous version of secret is not deleted or changed\n===")
  @Test
  public void shouldDeployNewSecretVersion() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "secret " + secretName + "-v000");

    manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .withValue("data.newfile", "SGVsbG8gd29ybGQK")
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "secret " + secretName + "-v001");

    // ------------------------- then --------------------------
    String secret = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName + "-v001");
    assertTrue(secret.contains("v001"), "Expected secret with name " + secretName + "-v001");
    secret = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName + "-v000");
    assertTrue(secret.contains("v000"), "Expected secret with name " + secretName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a configmap manifest with special annotation to avoid being versioned\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then configmap is deployed without version\n===")
  @Test
  public void shouldNotAddVersionToConfigmap() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .withValue(
                "metadata.annotations", ImmutableMap.of("strategy.spinnaker.io/versioned", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "configMap " + cmName);

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName);
    assertFalse(cm.contains("v000"), "Expected configmap with name " + cmName);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret manifest with special annotation to avoid being versioned\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then secret is deployed without version\n===")
  @Test
  public void shouldNotAddVersionToSecret() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .withValue(
                "metadata.annotations", ImmutableMap.of("strategy.spinnaker.io/versioned", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    deployAndWaitStable(body, ns, "secret " + secretName);

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName);
    assertFalse(cm.contains("v000"), "Expected secret with name " + secretName);
  }

  // ------------------------------------------------------------------------------------------------------
  // ------------------------------------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private void deployAndWaitStable(
      List<Map<String, Object>> reqBody, String targetNs, String... objectNames)
      throws InterruptedException {

    System.out.println("> Sending deploy manifest request");
    Response resp =
        given()
            .log()
            .uri()
            .contentType("application/json")
            .body(reqBody)
            .post(baseUrl() + "/kubernetes/ops");
    resp.then().statusCode(200);
    System.out.println("< Completed in " + resp.getTimeIn(TimeUnit.SECONDS) + " seconds");
    String taskId = resp.jsonPath().get("id");

    System.out.println("> Waiting for deploy task to complete");
    long start = System.currentTimeMillis();
    List<String> deployedObjectNames = new ArrayList<>();
    KubeTestUtils.repeatUntilTrue(
        () -> {
          Response respTask = given().log().uri().get(baseUrl() + "/task/" + taskId);
          if (respTask.statusCode() == 404) {
            return false;
          }
          respTask.then().statusCode(200);
          respTask.then().body("status.failed", is(false));
          deployedObjectNames.clear();
          deployedObjectNames.addAll(
              respTask
                  .jsonPath()
                  .getList(
                      "resultObjects.manifestNamesByNamespace." + targetNs + ".flatten()",
                      String.class));
          return respTask.jsonPath().getBoolean("status.completed");
        },
        30,
        TimeUnit.SECONDS,
        "Waited 30 seconds on GET /task/{id} to return \"status.completed: true\"");
    System.out.println(
        "< Deploy task completed in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

    assertEquals(
        Arrays.asList(objectNames),
        deployedObjectNames,
        "Expected object names deployed: "
            + Arrays.toString(objectNames)
            + " but were: "
            + deployedObjectNames);

    for (String objectName : objectNames) {
      System.out.println("> Sending force cache refresh request for object \"" + objectName + "\"");
      resp =
          given()
              .log()
              .uri()
              .contentType("application/json")
              .body(
                  ImmutableMap.of(
                      "account", ACCOUNT1_NAME,
                      "location", targetNs,
                      "name", objectName))
              .post(baseUrl() + "/cache/kubernetes/manifest");
      resp.then().statusCode(anyOf(is(200), is(202)));
      System.out.println("< Completed in " + resp.getTimeIn(TimeUnit.SECONDS) + " seconds");

      if (resp.statusCode() == 202) {
        System.out.println("> Waiting cache to be refreshed for object \"" + objectName + "\"");
        start = System.currentTimeMillis();
        KubeTestUtils.repeatUntilTrue(
            () -> {
              Response fcrWaitResp =
                  given().log().uri().get(baseUrl() + "/cache/kubernetes/manifest");
              fcrWaitResp.then().log().body(false);
              List<Object> list =
                  Stream.of(fcrWaitResp.as(Map[].class))
                      .filter(
                          it -> {
                            Map<String, Object> details = (Map<String, Object>) it.get("details");
                            String name = (String) details.get("name");
                            String account = (String) details.get("account");
                            String location = (String) details.get("location");
                            Number processedTime = (Number) it.get("processedTime");
                            return Objects.equals(ACCOUNT1_NAME, account)
                                && Objects.equals(targetNs, location)
                                && Objects.equals(objectName, name)
                                && processedTime != null
                                && processedTime.longValue() > -1;
                          })
                      .collect(Collectors.toList());
              return !list.isEmpty();
            },
            5,
            TimeUnit.MINUTES,
            "GET /cache/kubernetes/manifest did not returned processedTime > -1 for object \""
                + objectName
                + "\" after 5 minutes");
        System.out.println(
            "< Force cache refresh for \""
                + objectName
                + "\" completed in "
                + ((System.currentTimeMillis() - start) / 1000)
                + " seconds");
      } else {
        System.out.println(
            "< Force cache refresh for object \"" + objectName + "\" succeeded immediately");
      }

      System.out.println(
          "> Sending get manifest request for object \"" + objectName + "\" to check stability");
      start = System.currentTimeMillis();
      KubeTestUtils.repeatUntilTrue(
          () -> {
            Response respWait =
                given()
                    .log()
                    .uri()
                    .queryParam("includeEvents", false)
                    .get(
                        baseUrl()
                            + "/manifests/"
                            + ACCOUNT1_NAME
                            + "/"
                            + targetNs
                            + "/"
                            + objectName);
            JsonPath jsonPath = respWait.jsonPath();
            System.out.println(jsonPath.getObject("status", Map.class));
            respWait.then().statusCode(200).body("status.failed.state", is(false));
            return jsonPath.getBoolean("status.stable.state");
          },
          5,
          TimeUnit.MINUTES,
          "Waited 5 minutes on GET /manifest.. to return \"status.stable.state: true\"");
      System.out.println(
          "< Object \""
              + objectName
              + "\" stable in "
              + ((System.currentTimeMillis() - start) / 1000)
              + " seconds");
    }
  }
}
