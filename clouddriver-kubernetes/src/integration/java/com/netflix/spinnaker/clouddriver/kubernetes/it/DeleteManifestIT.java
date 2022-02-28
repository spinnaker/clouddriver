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

package com.netflix.spinnaker.clouddriver.kubernetes.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DeleteManifestIT extends BaseTest {

  private static String APP_1_NAME = "app1";
  private static String account1Ns;

  @BeforeAll
  public static void setUpAll() throws IOException, InterruptedException {
    account1Ns = kubeCluster.createNamespace(ACCOUNT1_NAME);
  }

  public List<Map<String, Object>> buildStaticRequestBody(String manifestName, Boolean cascading) {
    return KubeTestUtils.loadJson("classpath:requests/delete_manifest.json")
        .withValue("deleteManifest.app", APP_1_NAME)
        .withValue("deleteManifest.mode", "static")
        .withValue("deleteManifest.manifestName", manifestName)
        .withValue("deleteManifest.options.cascading", cascading)
        .withValue("deleteManifest.location", account1Ns)
        .withValue("deleteManifest.account", ACCOUNT1_NAME)
        .asList();
  }

  public List<Map<String, Object>> buildDynamicRequestBody(
      String manifestName, Boolean cascading, String cluster, String criteria, String kind) {
    return KubeTestUtils.loadJson("classpath:requests/delete_manifest.json")
        .withValue("deleteManifest.app", APP_1_NAME)
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

  public void deploy(final List<Map<String, Object>> manifest, String... objectNames)
      throws InterruptedException {
    List<Map<String, Object>> b =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP_1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), b, account1Ns, objectNames);
  }

  public void deployReplicaSets(byte amount) throws InterruptedException {
    List<Map<String, Object>> m =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml").asList();
    for (byte i = 0; i < amount; i++) {
      deploy(m, String.format("replicaSet nginx-v%03d", i));
    }
  }

  @DisplayName(
      ".\n===\n"
          + "Given an existing static target\n"
          + "When sending delete manifest request\n"
          + "It should wait until the target is deleted\n===")
  @Test
  public void staticTarget() throws IOException, InterruptedException {
    final String sn = "mysecret";
    Map<String, Object> sm =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", sn)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", sm);
    List<Map<String, Object>> rb = buildStaticRequestBody(String.format("secret %s", sn), true);
    List<String> on = KubeTestUtils.delete(baseUrl(), rb, account1Ns);
    assertEquals(1, on.size());
  }

  @DisplayName(
      ".\n===\n"
          + "Given a existing dynamic target\n"
          + "When sending delete manifest request\n"
          + "It should wait until the target is deleted\n===")
  @Test
  public void dynamicTarget() throws InterruptedException, IOException {
    deployReplicaSets((byte) 2);
    List<Map<String, Object>> rb =
        buildDynamicRequestBody(
            "replicaSet nginx-v000", true, "replicaSet nginx", "newest", "replicaSet");
    List<String> on = KubeTestUtils.delete(baseUrl(), rb, account1Ns);
    assertEquals(1, on.size());
  }

  @DisplayName(
      ".\n===\n"
          + "Given a existing deployment manifest\n"
          + "When sending delete manifest request\n"
          + "without the cascading option enable\n"
          + "It should delete the deployment and on the background the pods\n===")
  @Test
  public void withoutCascading() throws IOException, InterruptedException {
    String dn = "myapp";
    Map<String, Object> dm =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", dn)
            .withValue("spec.replicas", 3)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", dm);
    kubeCluster.execKubectl(
        String.format(
            "wait deployment -n %s %s --for condition=Available=True --timeout=600s",
            account1Ns, dn));
    List<Map<String, Object>> rb =
        buildStaticRequestBody(String.format("deployment %s", dn), false);
    List<String> on = KubeTestUtils.delete(baseUrl(), rb, account1Ns);
    String ps = kubeCluster.execKubectl(String.format("-n %s get pods -l=app=%s", account1Ns, dn));
    assertTrue(on.size() == 1 && ps.contains("Running"));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a NOT existing deployment manifest\n"
          + "When sending delete manifest request\n"
          + "It should return a not found resource\n===")
  @Test
  public void notFound() throws InterruptedException {
    List<Map<String, Object>> rb = buildStaticRequestBody("deployment notExists", true);
    List<String> on = KubeTestUtils.delete(baseUrl(), rb, account1Ns);
    assertEquals(0, on.size());
  }
}
