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

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.clouddriver.kubernetes.it.helpers.TestManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.it.requests.*;
import io.restassured.response.Response;
import java.io.IOException;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DeployManifestIT extends BaseTest {

  @DisplayName(
      "\n"
          + "Given a nginx deployment manifest with no namespace set\n"
          + "  And a namespace override\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx pod is up and running in the overridden namespace")
  @Test
  public void shouldDeployManifestFromText() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    TestManifest manifest = new TestManifest("classpath:manifests/deployment_nginx.yml");
    String overrideNamespace = "overridenns";
    kubeCluster.execKubectl("create ns " + overrideNamespace);

    // ------------------------- when --------------------------
    String taskId =
        new DeployManifestRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withNamespace(overrideNamespace)
            .withSpinApplication(APP1_NAME)
            .withManifest(manifest)
            .executeAndValidate();
    new WaitForTaskRequest(baseUrl()).withTaskId(taskId).executeAndValidate();

    ForceCacheRefreshRequest req =
        new ForceCacheRefreshRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withLocation(overrideNamespace)
            .withName("deployment nginx");
    Response resp = req.executeAndValidate();
    if (resp.statusCode() == 202) {
      new WaitForCacheRefreshedRequest(baseUrl()).withRequest(req).executeAndValidate();
    }

    new WaitManifestStableRequest(baseUrl())
        .withAccount(ACCOUNT1_NAME)
        .withNamespace(overrideNamespace)
        .withObjectName("deployment nginx")
        .executeAndValidate();

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + overrideNamespace + " get pods");
    System.out.println("kubectl -n " + overrideNamespace + " get pods:\n" + pods);
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + overrideNamespace
                + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
  }

  @DisplayName(
      "\n"
          + "Given a nginx deployment manifest with no namespace set\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx pod is up and running in the default namespace")
  @Test
  public void shouldDeployManifestToDefaultNs() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    TestManifest manifest = new TestManifest("classpath:manifests/deployment_nginx.yml");

    // ------------------------- when --------------------------
    String taskId =
        new DeployManifestRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withSpinApplication(APP1_NAME)
            .withManifest(manifest)
            .executeAndValidate();
    new WaitForTaskRequest(baseUrl()).withTaskId(taskId).executeAndValidate();

    ForceCacheRefreshRequest req =
        new ForceCacheRefreshRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withLocation("default")
            .withName("deployment nginx");
    Response resp = req.executeAndValidate();
    if (resp.statusCode() == 202) {
      new WaitForCacheRefreshedRequest(baseUrl()).withRequest(req).executeAndValidate();
    }

    new WaitManifestStableRequest(baseUrl())
        .withAccount(ACCOUNT1_NAME)
        .withNamespace("default")
        .withObjectName("deployment nginx")
        .executeAndValidate();

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n default get pods");
    System.out.println("kubectl -n default get pods:\n" + pods);
    String readyPods =
        kubeCluster.execKubectl(
            "-n default" + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
  }

  @DisplayName(
      "\n"
          + "Given a document with multiple manifest definitions\n"
          + "When sending deploy manifest request\n"
          + "  And sending force cache refresh request\n"
          + "  And waiting on manifest stable\n"
          + "Then nginx service and pod exist in the target cluster")
  @Test
  public void shouldDeployMultidocManifest() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String ns = "multimanifest";
    TestManifest manifest =
        new TestManifest("classpath:manifests/multi_nginx.yml").withNamespace(ns);
    kubeCluster.execKubectl("create ns " + ns);

    // ------------------------- when --------------------------
    String taskId =
        new DeployManifestRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withSpinApplication(APP1_NAME)
            .withManifest(manifest)
            .executeAndValidate();
    new WaitForTaskRequest(baseUrl()).withTaskId(taskId).executeAndValidate();

    ForceCacheRefreshRequest req =
        new ForceCacheRefreshRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withLocation(ns)
            .withName("deployment nginx");
    Response resp = req.executeAndValidate();
    if (resp.statusCode() == 202) {
      new WaitForCacheRefreshedRequest(baseUrl()).withRequest(req).executeAndValidate();
    }
    req =
        new ForceCacheRefreshRequest(baseUrl())
            .withAccount(ACCOUNT1_NAME)
            .withLocation(ns)
            .withName("service nginx");
    resp = req.executeAndValidate();
    if (resp.statusCode() == 202) {
      new WaitForCacheRefreshedRequest(baseUrl()).withRequest(req).executeAndValidate();
    }

    new WaitManifestStableRequest(baseUrl())
        .withAccount(ACCOUNT1_NAME)
        .withNamespace(ns)
        .withObjectName("deployment nginx")
        .executeAndValidate();
    new WaitManifestStableRequest(baseUrl())
        .withAccount(ACCOUNT1_NAME)
        .withNamespace(ns)
        .withObjectName("service nginx")
        .executeAndValidate();

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    System.out.println("kubectl -n " + ns + " get pods:\n" + pods);
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment nginx -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for nginx deployment. Pods:\n" + pods);
    String services = kubeCluster.execKubectl("-n " + ns + " get services");
    System.out.println("kubectl -n " + ns + " get services:\n" + services);
    assertTrue(
        Strings.isNotEmpty(kubeCluster.execKubectl("-n " + ns + " get services nginx")),
        "Expected service nginx to exist. Services: " + services);
  }
}
