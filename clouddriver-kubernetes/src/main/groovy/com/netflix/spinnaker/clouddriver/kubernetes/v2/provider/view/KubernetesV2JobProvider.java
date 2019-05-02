/*
 * Copyright 2019 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.provider.view;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCacheDataConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.model.KubernetesV2Manifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.model.KubernetesV2JobStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.model.JobProvider;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.model.Manifest;
import com.netflix.spinnaker.clouddriver.model.ManifestProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Job;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KubernetesV2JobProvider implements JobProvider<KubernetesV2JobStatus> {

  @Getter
  private String platform = "kubernetes";
  private AccountCredentialsProvider accountCredentialsProvider;
  private List<ManifestProvider> manifestProviderList;

  KubernetesV2JobProvider(AccountCredentialsProvider accountCredentialsProvider, List<ManifestProvider> manifestProviderList) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.manifestProviderList = manifestProviderList;
  }

  public KubernetesV2JobStatus collectJob(String account, String location, String id) {

    KubernetesV2Credentials credentials = (KubernetesV2Credentials) accountCredentialsProvider.getCredentials(account).getCredentials();
    List<Manifest> manifests = manifestProviderList.stream()
      .map(p -> p.getManifest(account, location, id))
      .filter( m -> m != null)
      .collect(Collectors.toList());

    if (manifests.isEmpty()) {
      throw new IllegalStateException("Could not find Kubernetes manifest " + id + " in namespace " + location);
    }

    KubernetesManifest jobManifest = ((KubernetesV2Manifest) manifests.get(0)).getManifest();
    V1Job job = KubernetesCacheDataConverter.getResource(jobManifest, V1Job.class);
    KubernetesV2JobStatus jobStatus = new KubernetesV2JobStatus(job, account);

    StringBuilder logs = new StringBuilder();
    job.getSpec().getTemplate().getSpec().getContainers().stream()
      .forEach(c -> {
        logs.append("=====" + c.getName() + "=======");
        try {
          logs.append(credentials.jobLogs(location, job.getMetadata().getName()));
        } catch (Exception e) {
          logs.append(e.getMessage());
        }
        logs.append("\n\n");
      });

    jobStatus.setLogs(logs.toString());

    return jobStatus;
  }

  public Map<String, Object> getFileContents(String account, String location, String id, String filename) {
    KubernetesV2Credentials credentials = (KubernetesV2Credentials) accountCredentialsProvider.getCredentials(account).getCredentials();

    List<Manifest> manifests = manifestProviderList.stream()
      .map(p -> p.getManifest(account, location, id))
      .filter( m -> m != null)
      .collect(Collectors.toList());

    if (manifests.isEmpty()) {
      log.warn("Could not find Kubernetes manifest {} in namespace {}", id, location);
      return null;
    }

    KubernetesManifest jobManifest = ((KubernetesV2Manifest) manifests.get(0)).getManifest();
    V1Job job = KubernetesCacheDataConverter.getResource(jobManifest, V1Job.class);
    String logContents = credentials.jobLogs(location, job.getMetadata().getName());

    Map props = null;
    try {
      props = PropertyParser.extractPropertiesFromLog(logContents);
    } catch(Exception e) {
      log.error("Couldn't parse properties for account {} at {}", account, location);
    }

    return props;
  }

  public void cancelJob(String account, String location, String id) {
    throw new NotImplementedException("cancelJob is not implemented for the V2 Kubrenetes provider");
  }




}
