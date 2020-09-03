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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    String overrideNamespace = "overridenns";
    kubeCluster.execKubectl("create ns " + overrideNamespace);

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
    String ns = "multimanifest";
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/multi_nginx.yml")
            .withValue("metadata.namespace", ns)
            .asList();

    kubeCluster.execKubectl("create ns " + ns);

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
    String ns = "deployupdate";
    String oldImage = "index.docker.io/library/nginx:1.14.0";
    String newImage = "index.docker.io/library/nginx:1.15.0";

    kubeCluster.execKubectl("create ns " + ns);
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

  // ------------------------------------------------------------------------------------------------------
  // ------------------------------------------------------------------------------------------------------

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
    KubeTestUtils.repeatUntilTrue(
        () -> {
          Response respTask = given().log().uri().get(baseUrl() + "/task/" + taskId);
          if (respTask.statusCode() == 404) {
            return false;
          }
          respTask.then().statusCode(200);
          respTask.then().body("status.failed", is(false));
          return respTask.jsonPath().getBoolean("status.completed");
        },
        30,
        TimeUnit.SECONDS,
        "Waited 30 seconds on GET /task/{id} to return \"status.completed: true\"");
    System.out.println(
        "< Deploy task completed in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

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
              JsonPath jsonPath = fcrWaitResp.jsonPath();
              List<Object> list =
                  jsonPath.get(
                      "findAll { it -> it.details.account == \""
                          + ACCOUNT1_NAME
                          + "\" && it.details.location == \""
                          + targetNs
                          + "\" && it.details.name == \""
                          + objectName
                          + "\" && it.processedTime > -1 }");
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
            respWait.then().statusCode(200).body("status.failed.state", is(false));
            return respWait.jsonPath().getBoolean("status.stable.state");
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
