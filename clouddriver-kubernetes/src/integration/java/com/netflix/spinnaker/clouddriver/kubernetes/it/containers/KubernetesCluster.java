/*
 * Copyright Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.it.containers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.util.FileCopyUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.yaml.snakeyaml.Yaml;

public class KubernetesCluster extends GenericContainer<KubernetesCluster> {

  private static final String DOCKER_IMAGE = "rancher/k3s:v1.17.11-k3s1";
  private static final String KUBECFG_IN_CONTAINER = "/etc/rancher/k3s/k3s.yaml";

  private static final Map<String, KubernetesCluster> instances = new HashMap<>();

  private Path kubecfgPath;
  private final String accountName;

  public static KubernetesCluster getInstance(String accountName) {
    return instances.computeIfAbsent(accountName, KubernetesCluster::new);
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
            "0.0.0.0")
        .waitingFor(Wait.forLogMessage(".*Wrote kubeconfig .*", 1));
  }

  public Path getKubecfgPath() {
    return kubecfgPath;
  }

  public String execKubectl(String args) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder();
    List<String> cmd = new ArrayList<>();
    cmd.add("sh");
    cmd.add("-c");
    cmd.add(
        "${PROJECT_ROOT}/clouddriver-kubernetes/src/integration/resources/kubectl-wrapper.sh --kubeconfig="
            + kubecfgPath
            + " "
            + args);
    builder.command(cmd);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    Reader reader = new InputStreamReader(process.getInputStream(), UTF_8);
    String output = FileCopyUtils.copyToString(reader);
    if (!process.waitFor(1, TimeUnit.MINUTES)) {
      fail("Command %s did not return after one minute", cmd);
    }
    assertThat(process.exitValue())
        .as("Running %s returned non-zero exit code. Output:\n%s", cmd, output)
        .isEqualTo(0);
    System.out.println("kubectl " + args + ":\n" + output);
    return output.trim();
  }

  @Override
  public void start() {
    super.start();
    String containerName = getContainerInfo().getName().replaceAll("/", "");
    System.setProperty(this.accountName + "_containername", containerName);
    try {
      this.kubecfgPath = copyKubecfgFromCluster(containerName);
      fixKubeEndpoint(this.kubecfgPath);
    } catch (IOException e) {
      throw new RuntimeException("Unable to initialize kubectl or kubeconfig.yml files", e);
    }
  }

  @Override
  public void stop() {
    super.stop();
    try {
      Files.deleteIfExists(this.kubecfgPath);
    } catch (IOException e) {
      /* ignored */
    }
  }

  private Path copyKubecfgFromCluster(String containerName) throws IOException {
    Path myKubeconfig =
        Paths.get(
            System.getenv("PROJECT_ROOT"),
            "clouddriver-kubernetes",
            "build",
            "kubeconfigs",
            "kubecfg-" + containerName + ".yml");
    Files.createDirectories(myKubeconfig.getParent());
    copyFileFromContainer(KUBECFG_IN_CONTAINER, myKubeconfig.toAbsolutePath().toString());
    return myKubeconfig;
  }

  @SuppressWarnings("unchecked")
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
