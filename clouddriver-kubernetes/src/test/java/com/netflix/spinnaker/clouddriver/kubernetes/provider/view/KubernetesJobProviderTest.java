/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesManifestContainer;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesJobStatus;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KubernetesJobProviderTest {
  private String getResource(String name) {
    try {
      return Resources.toString(
          KubernetesJobProviderTest.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void testFailedJobWithContainerLogsAvailable() {
    KubernetesManifestProvider mockManifestProvider = mock(KubernetesManifestProvider.class);
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base-with-completions.yml"), KubernetesManifest.class);
    KubernetesManifest overlay =
        Yaml.loadAs(getResource("failed-job.yml"), KubernetesManifest.class);
    testManifest.putAll(overlay);

    doReturn(
            KubernetesManifestContainer.builder()
                .account("mock_account")
                .name("a")
                .manifest(testManifest)
                .build())
        .when(mockManifestProvider)
        .getManifest(anyString(), anyString(), anyString(), anyBoolean());

    AccountCredentialsProvider credentialsProvider = mock(AccountCredentialsProvider.class);
    AccountCredentials accountCredentials = mock(AccountCredentials.class);
    KubernetesCredentials mockCredentials = mock(KubernetesCredentials.class);

    doReturn(ImmutableList.of(testManifest)).when(mockCredentials).list(any(), isNull(), any());

    doReturn(mockCredentials).when(accountCredentials).getCredentials();
    doReturn(accountCredentials).when(credentialsProvider).getCredentials(anyString());

    KubernetesJobProvider kubernetesJobProvider =
        new KubernetesJobProvider(credentialsProvider, mockManifestProvider);
    KubernetesJobStatus jobStatus = kubernetesJobProvider.collectJob("mock_account", "a", "b");

    assertNotNull(jobStatus.getJobState());
    assertEquals(jobStatus.getJobState(), JobState.Failed);

    assertThat(jobStatus.getMessage()).isEqualTo("Job has reached the specified backoff limit");
    assertThat(jobStatus.getReason()).isEqualTo("BackoffLimitExceeded");
    assertThat(jobStatus.getPods().size()).isEqualTo(1);
    KubernetesJobStatus.PodStatus podStatus = jobStatus.getPods().get(0);
    assertThat(podStatus.getContainerExecutionDetails().size()).isEqualTo(2);
    Optional<KubernetesJobStatus.ContainerExecutionDetails> failedContainer =
        podStatus.getContainerExecutionDetails().stream()
            .filter(c -> c.getName().equals("some-container-name"))
            .findFirst();

    Assertions.assertFalse(failedContainer.isEmpty());
    assertThat(failedContainer.get().getLogs())
        .isEqualTo(
            "Failed to download the file: foo.\n"
                + "GET Request failed with status code', 404, 'Expected', <HTTPStatus.OK: 200>)\n");
    assertThat(failedContainer.get().getStatus()).isEqualTo("Error");
    assertThat(failedContainer.get().getExitCode()).isEqualTo("1");
  }

  @Test
  void testFailedJobWithoutContainerLogs() {
    KubernetesManifestProvider mockManifestProvider = mock(KubernetesManifestProvider.class);
    KubernetesManifest testManifest =
        Yaml.loadAs(getResource("base-with-completions.yml"), KubernetesManifest.class);
    KubernetesManifest overlay =
        Yaml.loadAs(getResource("runjob-deadline-exceeded.yml"), KubernetesManifest.class);
    testManifest.putAll(overlay);
    doReturn(
            KubernetesManifestContainer.builder()
                .account("mock_account")
                .name("a")
                .manifest(testManifest)
                .build())
        .when(mockManifestProvider)
        .getManifest(anyString(), anyString(), anyString(), anyBoolean());

    AccountCredentialsProvider credentialsProvider = mock(AccountCredentialsProvider.class);
    AccountCredentials accountCredentials = mock(AccountCredentials.class);
    KubernetesCredentials mockCredentials = mock(KubernetesCredentials.class);

    doReturn(ImmutableList.of(testManifest)).when(mockCredentials).list(any(), isNull(), any());

    doReturn(mockCredentials).when(accountCredentials).getCredentials();
    doReturn(accountCredentials).when(credentialsProvider).getCredentials(anyString());

    KubernetesJobProvider kubernetesJobProvider =
        new KubernetesJobProvider(credentialsProvider, mockManifestProvider);
    KubernetesJobStatus jobStatus = kubernetesJobProvider.collectJob("mock_account", "a", "b");

    assertNotNull(jobStatus.getJobState());
    assertEquals(jobStatus.getJobState(), JobState.Failed);

    assertThat(jobStatus.getMessage()).isEqualTo("Job was active longer than specified deadline");
    assertThat(jobStatus.getReason()).isEqualTo("DeadlineExceeded");
  }
}
