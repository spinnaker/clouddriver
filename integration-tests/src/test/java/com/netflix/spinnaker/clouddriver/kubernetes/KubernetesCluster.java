/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.testcontainers.containers.GenericContainer;
import org.yaml.snakeyaml.Yaml;

public class KubernetesCluster extends GenericContainer {

  private static final String DOCKER_IMAGE = "rancher/k3s:v1.18.8-k3s1";
  private static final String KUBECFG_IN_CONTAINER = "/etc/rancher/k3s/k3s.yaml";

  private static final Map<String, KubernetesCluster> instances = new HashMap<>();

  private Path kubecfgPath;
  private final String accountName;

  public static KubernetesCluster getInstance(String accountName) {
    if (instances.get(accountName) == null) {
      instances.put(accountName, new KubernetesCluster(accountName));
    }
    return instances.get(accountName);
  }

  private KubernetesCluster(String accountName) {
    super(DOCKER_IMAGE);
    this.accountName = accountName;

    // arguments to docker run
    Map<String, String> tmpfs = new HashMap<>();
    tmpfs.put("/run", "rw");
    tmpfs.put("/var/run", "rw");
    withTmpFs(tmpfs)
        .withPrivilegedMode(true)
        .withExposedPorts(6443)
        .withCommand(
            "server",
            "--kubelet-arg=eviction-hard=imagefs.available<1%,nodefs.available<1%",
            "--kubelet-arg=eviction-minimum-reclaim=imagefs.available=1%,nodefs.available=1%",
            "--tls-san",
            "0.0.0.0");
  }

  public Path getKubecfgPath() {
    return kubecfgPath;
  }

  @Override
  public void start() {
    super.start();
    String containerName = getContainerInfo().getName().replaceAll("/", "");
    System.setProperty(this.accountName + "_containername", containerName);
    try {
      this.kubecfgPath = copyKubecfgFromCluster(containerName);
      fixKubeEndpoint(this.kubecfgPath);
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Unable to initialize kubectl or kubeconfig.yml files", e);
    }
  }

  @Override
  public void stop() {
    super.stop();
    try {
      Files.deleteIfExists(this.kubecfgPath);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private Path copyKubecfgFromCluster(String containerName)
      throws IOException, InterruptedException {
    Path myKubeconfig =
        Paths.get(
            System.getenv("PROJECT_ROOT"),
            "integration-tests",
            "build",
            "kubeconfigs",
            "kubecfg-" + containerName + ".yml");
    Files.createDirectories(myKubeconfig.getParent());
    // wait until the kubeconfig file is generated... but don't wait forever
    int loops = 0;
    while (execInContainer("ls", KUBECFG_IN_CONTAINER).getExitCode() != 0) {
      Thread.sleep(1000);
      loops++;
      if (loops > 30) {
        throw new RuntimeException(
            "Waited too much for auto generated kubeconfig file to be ready");
      }
    }
    copyFileFromContainer(KUBECFG_IN_CONTAINER, myKubeconfig.toAbsolutePath().toString());
    return myKubeconfig;
  }

  private void fixKubeEndpoint(Path kubecfgPath) throws IOException {
    String kubeEndpoint = "https://" + getHost() + ":" + getMappedPort(6443);
    Yaml yaml = new Yaml();
    InputStream inputStream = Files.newInputStream(kubecfgPath);
    Map<String, Object> obj = yaml.load(inputStream);
    List<Map<String, Map<String, String>>> clusters =
        (List<Map<String, Map<String, String>>>) obj.get("clusters");
    clusters.get(0).get("cluster").put("server", kubeEndpoint);
    yaml.dump(obj, Files.newBufferedWriter(kubecfgPath));
  }
}
