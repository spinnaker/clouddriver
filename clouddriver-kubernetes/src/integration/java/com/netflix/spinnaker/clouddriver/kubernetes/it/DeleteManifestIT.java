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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.http.HttpStatus;

public class DeleteManifestIT extends BaseTest {

  private static String account1Ns;

  @BeforeAll
  public static void setUpAll() throws IOException, InterruptedException {
    account1Ns = kubeCluster.createNamespace(ACCOUNT1_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret deployed outside of Spinnaker\n"
          + "When sending a delete manifest operation with static target\n"
          + "Then the secret is deleted\n===")
  @Test
  public void shouldDeleteByStaticTarget() throws IOException, InterruptedException {
    final String s = "secret";
    final String sn = "mysecret";
    Map<String, Object> sm =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", sn)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", sm);
    List<Map<String, Object>> rb = buildStaticRequestBody(String.format("%s %s", s, sn), true);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ss =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, s, sn));
    assertTrue(ss.isBlank() && on.size() == 1 && on.get(0).equals(String.format("%s %s", s, sn)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using newest dynamic target criteria\n"
          + "Then the newest replicaset is deleted\n===")
  @Test
  public void shouldDeleteNewestByDynamicTarget() throws IOException, InterruptedException {
    String rs = "replicaSet";
    String c = "newest";
    String rsn = String.format("nginx-%s-test", c);
    String rdn = String.format("%s-v001", rsn);
    List<Map<String, Object>> m =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.name", rsn)
            .asList();
    List<Map<String, Object>> b =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", m)
            .asList();
    for (byte i = 0; i < 2; i++) {
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), b, account1Ns, String.format("replicaSet %s-v%03d", rsn, i));
    }
    List<Map<String, Object>> rb =
        buildDynamicRequestBody(
            String.format("%s %s", rs, rdn), true, String.format("%s %s", rs, rsn), c, rs);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ss =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, rs, rdn));
    assertTrue(ss.isBlank() && on.size() == 1 && on.get(0).equals(String.format("%s %s", rs, rdn)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using second newest dynamic target criteria\n"
          + "Then the second newest replicaset is deleted\n===")
  @Test
  public void shouldDeleteSecondNewestByDynamicTarget() throws IOException, InterruptedException {
    String rs = "replicaSet";
    String c = "second-newest";
    String rsn = String.format("nginx-%s-test", c);
    String rdn = String.format("%s-v000", rsn);
    List<Map<String, Object>> m =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.name", rsn)
            .asList();
    List<Map<String, Object>> b =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", m)
            .asList();
    for (byte i = 0; i < 2; i++) {
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), b, account1Ns, String.format("replicaSet %s-v%03d", rsn, i));
    }
    List<Map<String, Object>> rb =
        buildDynamicRequestBody(
            String.format("%s %s", rs, rdn), true, String.format("%s %s", rs, rsn), c, rs);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ss =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, rs, rdn));
    assertTrue(ss.isBlank() && on.size() == 1 && on.get(0).equals(String.format("%s %s", rs, rdn)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using oldest dynamic target criteria\n"
          + "Then the oldest replicaset is deleted\n===")
  @Test
  public void shouldDeleteOldestByDynamicTarget() throws IOException, InterruptedException {
    String rs = "replicaSet";
    String c = "oldest";
    String rsn = String.format("nginx-%s-test", c);
    String rdn = String.format("%s-v000", rsn);
    List<Map<String, Object>> m =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.name", rsn)
            .asList();
    List<Map<String, Object>> b =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", m)
            .asList();
    for (byte i = 0; i < 2; i++) {
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), b, account1Ns, String.format("replicaSet %s-v%03d", rsn, i));
    }
    List<Map<String, Object>> rb =
        buildDynamicRequestBody(
            String.format("%s %s", rs, rdn), true, String.format("%s %s", rs, rsn), c, rs);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ss =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, rs, rdn));
    assertTrue(ss.isBlank() && on.size() == 1 && on.get(0).equals(String.format("%s %s", rs, rdn)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using largest dynamic target criteria\n"
          + "Then the replicaset that has the greater amount of replicas is deleted\n===")
  @Test
  public void shouldDeleteLargestByDynamicTarget() throws IOException, InterruptedException {
    String rs = "replicaSet";
    String c = "largest";
    String rsn = String.format("nginx-%s-test", c);
    String rdn = String.format("%s-v001", rsn);
    for (byte i = 0; i < 2; i++) {
      List<Map<String, Object>> m =
          KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
              .withValue("metadata.name", rsn)
              .withValue("spec.replicas", i)
              .asList();
      List<Map<String, Object>> b =
          KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
              .withValue("deployManifest.account", ACCOUNT1_NAME)
              .withValue("deployManifest.namespaceOverride", account1Ns)
              .withValue("deployManifest.moniker.app", APP1_NAME)
              .withValue("deployManifest.manifests", m)
              .asList();
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), b, account1Ns, String.format("replicaSet %s-v%03d", rsn, i));
    }
    List<Map<String, Object>> rb =
        buildDynamicRequestBody(
            String.format("%s %s", rs, rdn), true, String.format("%s %s", rs, rsn), c, rs);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ss =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, rs, rdn));
    assertTrue(ss.isBlank() && on.size() == 1 && on.get(0).equals(String.format("%s %s", rs, rdn)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using smallest dynamic target criteria\n"
          + "Then the replicaset that has the lower amount of replicas is deleted\n===")
  @Test
  public void shouldDeleteSmallestByDynamicTarget() throws IOException, InterruptedException {
    String rs = "replicaSet";
    String c = "smallest";
    String rsn = String.format("nginx-%s-test", c);
    String rdn = String.format("%s-v000", rsn);
    for (byte i = 0; i < 2; i++) {
      List<Map<String, Object>> m =
          KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
              .withValue("metadata.name", rsn)
              .withValue("spec.replicas", i)
              .asList();
      List<Map<String, Object>> b =
          KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
              .withValue("deployManifest.account", ACCOUNT1_NAME)
              .withValue("deployManifest.namespaceOverride", account1Ns)
              .withValue("deployManifest.moniker.app", APP1_NAME)
              .withValue("deployManifest.manifests", m)
              .asList();
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), b, account1Ns, String.format("replicaSet %s-v%03d", rsn, i));
    }
    List<Map<String, Object>> rb =
        buildDynamicRequestBody(
            String.format("%s %s", rs, rdn), true, String.format("%s %s", rs, rsn), c, rs);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ss =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, rs, rdn));
    assertTrue(ss.isBlank() && on.size() == 1 && on.get(0).equals(String.format("%s %s", rs, rdn)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with 3 replicas outside of Spinnaker\n"
          + "When sending a delete manifest operation without cascading option enable\n"
          + "Then just the deployment should be remove at once\n===")
  @Test
  public void shouldDeleteWithoutCascading() throws IOException, InterruptedException {
    String d = "deployment";
    String dn = "myapp";
    Map<String, Object> dm =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", dn)
            .withValue("spec.replicas", 3)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", dm);
    kubeCluster.execKubectl(
        String.format(
            "wait %s -n %s %s --for condition=Available=True --timeout=600s", d, account1Ns, dn));
    List<Map<String, Object>> rb = buildStaticRequestBody(String.format("%s %s", d, dn), false);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    String ps = kubeCluster.execKubectl(String.format("-n %s get pods -l=app=%s", account1Ns, dn));
    assertTrue(
        on.size() == 1
            && on.get(0).equals(String.format("%s %s", d, dn))
            && ps.contains("Running"));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a NOT existing static deployment manifest\n"
          + "When sending a delete manifest request\n"
          + "Then it should return any deleted deployment\n===")
  @Test
  public void shouldNotDeleteStaticTarget() throws InterruptedException {
    List<Map<String, Object>> rb = buildStaticRequestBody("deployment notExists", true);
    List<String> on = KubeTestUtils.sendOperation(baseUrl(), rb, account1Ns);
    assertEquals(0, on.size());
  }

  @DisplayName(
      ".\n===\n"
          + "Given a NOT existing dynamic replicaSet manifest\n"
          + "When sending a delete manifest operation using smallest dynamic target criteria\n"
          + "Then it gets a 404 while fetching the manifest\n===")
  @Test
  public void shouldNotFoundDynamicTarget() throws InterruptedException {
    String rs = "replicaSet";
    String c = "smallest";
    String rsn = String.format("not-exists-%s-test", c);
    String rdn = String.format("%s-v000", rsn);
    assertThrows(
        AssertionFailedError.class,
        () -> {
          KubeTestUtils.repeatUntilTrue(
              () -> {
                String url =
                    String.format(
                        "%s/manifests/%s/%s/%s %s", baseUrl(), ACCOUNT1_NAME, account1Ns, rs, rdn);
                Response respWait = given().queryParam("includeEvents", false).get(url);
                JsonPath jsonPath = respWait.jsonPath();
                if (respWait.statusCode() == HttpStatus.NOT_FOUND.value()) {
                  return false;
                }
                respWait.then().statusCode(200).body("status.failed.state", is(false));
                return jsonPath.getBoolean("status.stable.state");
              },
              1,
              TimeUnit.MINUTES,
              "Waited 1 minutes on GET /manifest.. to return \"status.stable.state: true\"");
        });
  }

  private List<Map<String, Object>> buildStaticRequestBody(String manifestName, Boolean cascading) {
    return KubeTestUtils.loadJson("classpath:requests/delete_manifest.json")
        .withValue("deleteManifest.app", APP1_NAME)
        .withValue("deleteManifest.mode", "static")
        .withValue("deleteManifest.manifestName", manifestName)
        .withValue("deleteManifest.options.cascading", cascading)
        .withValue("deleteManifest.location", account1Ns)
        .withValue("deleteManifest.account", ACCOUNT1_NAME)
        .asList();
  }

  private List<Map<String, Object>> buildDynamicRequestBody(
      String manifestName, Boolean cascading, String cluster, String criteria, String kind) {
    return KubeTestUtils.loadJson("classpath:requests/delete_manifest.json")
        .withValue("deleteManifest.app", APP1_NAME)
        .withValue("deleteManifest.mode", "dynamic")
        .withValue("deleteManifest.cluster", cluster)
        .withValue("deleteManifest.criteria", criteria)
        .withValue("deleteManifest.kind", kind)
        .withValue("deleteManifest.manifestName", manifestName)
        .withValue("deleteManifest.options.cascading", cascading)
        .withValue("deleteManifest.location", account1Ns)
        .withValue("deleteManifest.account", ACCOUNT1_NAME)
        .asList();
  }
}
