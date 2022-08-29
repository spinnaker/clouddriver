/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutionException;
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import com.netflix.spinnaker.clouddriver.jobs.JobResult.Result;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;

@RunWith(JUnitPlatform.class)
final class KubectlJobExecutorTest {
  private static final String NAMESPACE = "test-namespace";
  JobExecutor jobExecutor;
  KubernetesConfigurationProperties kubernetesConfigurationProperties;

  @BeforeEach
  public void setup() {
    jobExecutor = mock(JobExecutor.class);
    kubernetesConfigurationProperties = new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setBackOffInMs(500);
  }

  @ParameterizedTest(name = "{index} ==> retries enabled = {0}")
  @ValueSource(booleans = {true, false})
  void topPodEmptyOutput(boolean retriesEnabled) {
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder().result(Result.SUCCESS).output("").error("").build());

    KubernetesConfigurationProperties kubernetesConfigurationProperties =
        new KubernetesConfigurationProperties();
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(retriesEnabled);

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", "");
    assertThat(podMetrics).isEmpty();

    // should only be called once as no retries are performed
    verify(jobExecutor).runJob(any(JobRequest.class));

    if (retriesEnabled) {
      // verify retry registry
      assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
      RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
      assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
      assertThat(retryRegistry.getAllRetries().get(0).getName()).isEqualTo("mock-account.topPod.");

      // verify retry metrics
      Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
      assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
      // in this test, the action succeeded without retries. So number of unique calls == 1.
      assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
      assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
      assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);
    }
  }

  @Test
  void topPodMultipleContainers() throws Exception {
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.SUCCESS)
                .output(
                    Resources.toString(
                        KubectlJobExecutor.class.getResource("top-pod.txt"),
                        StandardCharsets.UTF_8))
                .error("")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", new KubernetesConfigurationProperties());
    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "");
    assertThat(podMetrics).hasSize(2);

    ImmutableSetMultimap<String, ContainerMetric> expectedMetrics =
        ImmutableSetMultimap.<String, ContainerMetric>builder()
            .putAll(
                "spinnaker-io-nginx-v000-42gnq",
                ImmutableList.of(
                    new ContainerMetric(
                        "spinnaker-github-io",
                        ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "2Mi")),
                    new ContainerMetric(
                        "istio-proxy",
                        ImmutableMap.of("CPU(cores)", "3m", "MEMORY(bytes)", "28Mi")),
                    new ContainerMetric(
                        "istio-init", ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "0Mi"))))
            .putAll(
                "spinnaker-io-nginx-v001-jvkgb",
                ImmutableList.of(
                    new ContainerMetric(
                        "spinnaker-github-io",
                        ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "2Mi")),
                    new ContainerMetric(
                        "istio-proxy",
                        ImmutableMap.of("CPU(cores)", "32m", "MEMORY(bytes)", "30Mi")),
                    new ContainerMetric(
                        "istio-init", ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "0Mi"))))
            .build();

    for (String pod : expectedMetrics.keys()) {
      Optional<KubernetesPodMetric> podMetric =
          podMetrics.stream()
              .filter(metric -> metric.getPodName().equals(pod))
              .filter(metric -> metric.getNamespace().equals(NAMESPACE))
              .findAny();
      assertThat(podMetric.isPresent()).isTrue();
      assertThat(podMetric.get().getContainerMetrics())
          .containsExactlyInAnyOrderElementsOf(expectedMetrics.get(pod));
    }
  }

  @DisplayName("test to verify how kubectl errors are handled when retries are disabled")
  @Test
  void kubectlJobExecutorErrorHandlingWhenRetriesAreDisabled() {
    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("some error")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    // then
    KubectlJobExecutor.KubectlException thrown =
        assertThrows(
            KubectlJobExecutor.KubectlException.class,
            () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", ""));

    assertTrue(thrown.getMessage().contains("some error"));
    // should only be called once as no retries are performed for this error
    verify(jobExecutor).runJob(any(JobRequest.class));
  }

  @DisplayName(
      "parameterized test to verify retry behavior for configured retryable errors that fail even after all "
          + "attempts are exhausted")
  @ParameterizedTest(
      name = "{index} ==> number of simultaneous executions of the action under test = {0}")
  @ValueSource(ints = {1, 10})
  void kubectlRetryHandlingForConfiguredErrorsThatContinueFailingAfterMaxRetryAttempts(
      int numberOfThreads) {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // to test log messages
    Logger logger = (Logger) LoggerFactory.getLogger(KubectlJobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();

    final ExecutorService executor =
        Executors.newFixedThreadPool(
            numberOfThreads,
            new ThreadFactoryBuilder()
                .setNameFormat(KubectlJobExecutorTest.class.getSimpleName() + "-%d")
                .build());

    final ArrayList<Future<ImmutableList<KubernetesPodMetric>>> futures =
        new ArrayList<>(numberOfThreads);

    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("Unable to connect to the server: net/http: TLS handshake timeout")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    for (int i = 1; i <= numberOfThreads; i++) {
      futures.add(
          executor.submit(
              () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod")));
    }

    // then
    for (Future<ImmutableList<KubernetesPodMetric>> future : futures) {
      try {
        future.get();
      } catch (final ExecutionException e) {
        assertTrue(e.getCause() instanceof KubectlJobExecutor.KubectlException);
        assertTrue(
            e.getMessage()
                .contains("Unable to connect to the server: net/http: TLS handshake timeout"));
      } catch (final InterruptedException ignored) {
      }
    }

    executor.shutdown();

    // verify that the kubectl job executor made max configured attempts per thread to execute the
    // action
    verify(
            jobExecutor,
            times(
                kubernetesConfigurationProperties.getJobExecutor().getRetries().getMaxAttempts()
                    * numberOfThreads))
        .runJob(any(JobRequest.class));

    // verify retry registry
    assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
    RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
    assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
    assertThat(retryRegistry.getAllRetries().get(0).getName())
        .isEqualTo("mock-account.topPod.test-pod");

    // verify retry metrics
    Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    // in this test, all threads failed. So number of unique failed calls == 1 per thread.
    assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(numberOfThreads);
    assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);

    // verify that no duplicate messages are shown in the logs
    List<ILoggingEvent> logsList = listAppender.list;
    List<ILoggingEvent> numberOfFailedRetryAttemptLogMessages =
        logsList.stream()
            .filter(
                iLoggingEvent ->
                    iLoggingEvent
                        .getFormattedMessage()
                        .contains(
                            "Action: mock-account.topPod.test-pod failed after "
                                + kubernetesConfigurationProperties
                                    .getJobExecutor()
                                    .getRetries()
                                    .getMaxAttempts()
                                + " attempts"))
            .collect(Collectors.toList());

    // we should only see 1 failed retry attempt message per thread
    assertThat(numberOfFailedRetryAttemptLogMessages.size()).isEqualTo(numberOfThreads);
  }

  @DisplayName(
      "parameterized test to verify retry behavior for errors that are not configured to be retryable")
  @ParameterizedTest(
      name = "{index} ==> number of simultaneous executions of the action under test = {0}")
  @ValueSource(ints = {1, 10})
  void kubectlMultiThreadedRetryHandlingForErrorsThatAreNotConfiguredToBeRetryable(
      int numberOfThreads) {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // to test log messages
    Logger logger = (Logger) LoggerFactory.getLogger(KubectlJobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();

    final ExecutorService executor =
        Executors.newFixedThreadPool(
            numberOfThreads,
            new ThreadFactoryBuilder()
                .setNameFormat(KubectlJobExecutorTest.class.getSimpleName() + "-%d")
                .build());

    final ArrayList<Future<ImmutableList<KubernetesPodMetric>>> futures =
        new ArrayList<>(numberOfThreads);

    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("un-retryable error")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    for (int i = 1; i <= numberOfThreads; i++) {
      futures.add(
          executor.submit(
              () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod")));
    }

    // then
    for (Future<ImmutableList<KubernetesPodMetric>> future : futures) {
      try {
        future.get();
      } catch (final ExecutionException e) {
        assertTrue(e.getCause() instanceof KubectlJobExecutor.KubectlException);
        assertTrue(e.getMessage().contains("un-retryable error"));
      } catch (final InterruptedException ignored) {
      }
    }

    executor.shutdown();

    // verify that the kubectl job executor tried once to execute the action once per thread
    verify(jobExecutor, times(numberOfThreads)).runJob(any(JobRequest.class));

    // verify retry registry
    assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
    RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
    assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
    assertThat(retryRegistry.getAllRetries().get(0).getName())
        .isEqualTo("mock-account.topPod.test-pod");

    // verify retry metrics
    Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
    // in this test, all threads failed without retrying. So number of unique failed calls == 1 per
    // thread.
    assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(numberOfThreads);

    // verify that no duplicate messages are shown in the logs
    List<ILoggingEvent> logsList = listAppender.list;
    List<ILoggingEvent> numberOfFailedRetryAttemptLogMessages =
        logsList.stream()
            .filter(
                iLoggingEvent ->
                    iLoggingEvent
                        .getFormattedMessage()
                        .contains(
                            "mock-account.topPod.test-pod will not be retried as retries are not enabled for error: un-retryable error"))
            .collect(Collectors.toList());

    // we should only see 1 failed retry attempt message per thread
    assertThat(numberOfFailedRetryAttemptLogMessages.size()).isEqualTo(numberOfThreads);
  }

  @Test
  void kubectlRetryHandlingForConfiguredErrorsThatSucceedAfterAFewRetries() throws IOException {
    // setup
    kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);

    // to test log messages
    Logger logger = (Logger) LoggerFactory.getLogger(KubectlJobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();

    // when
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.FAILURE)
                .output("")
                .error("Unable to connect to the server: net/http: TLS handshake timeout")
                .build())
        .thenReturn(
            JobResult.<String>builder()
                .result(Result.SUCCESS)
                .output(
                    Resources.toString(
                        KubectlJobExecutor.class.getResource("top-pod.txt"),
                        StandardCharsets.UTF_8))
                .error("")
                .build());

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    Collection<KubernetesPodMetric> podMetrics =
        kubectlJobExecutor.topPod(mockKubernetesCredentials(), NAMESPACE, "test-pod");

    // then

    // job executor should be called twice - as it failed on the first call but succeeded
    // in the second one
    verify(jobExecutor, times(2)).runJob(any(JobRequest.class));

    // verify retry registry
    assertTrue(kubectlJobExecutor.getRetryRegistry().isPresent());
    RetryRegistry retryRegistry = kubectlJobExecutor.getRetryRegistry().get();
    assertThat(retryRegistry.getAllRetries().size()).isEqualTo(1);
    assertThat(retryRegistry.getAllRetries().get(0).getName())
        .isEqualTo("mock-account.topPod.test-pod");

    // verify retry metrics
    Retry.Metrics retryMetrics = retryRegistry.getAllRetries().get(0).getMetrics();
    // in this test, the action succeeded eventually. So number of unique calls == 1.
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
    assertThat(retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(0);
    assertThat(retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(0);

    // verify that no duplicate messages are shown in the logs
    List<ILoggingEvent> logsList = listAppender.list;
    List<ILoggingEvent> numberOfSucceededRetryAttemptsLogMessages =
        logsList.stream()
            .filter(
                iLoggingEvent ->
                    iLoggingEvent
                        .getFormattedMessage()
                        .contains(
                            "Action: mock-account.topPod.test-pod succeeded after 1 retry attempt(s)"))
            .collect(Collectors.toList());

    // we should only see 1 succeeded retry attempt message
    assertThat(numberOfSucceededRetryAttemptsLogMessages.size()).isEqualTo(1);

    // verify output of the command
    assertThat(podMetrics).hasSize(2);
    ImmutableSetMultimap<String, ContainerMetric> expectedMetrics =
        ImmutableSetMultimap.<String, ContainerMetric>builder()
            .putAll(
                "spinnaker-io-nginx-v000-42gnq",
                ImmutableList.of(
                    new ContainerMetric(
                        "spinnaker-github-io",
                        ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "2Mi")),
                    new ContainerMetric(
                        "istio-proxy",
                        ImmutableMap.of("CPU(cores)", "3m", "MEMORY(bytes)", "28Mi")),
                    new ContainerMetric(
                        "istio-init", ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "0Mi"))))
            .putAll(
                "spinnaker-io-nginx-v001-jvkgb",
                ImmutableList.of(
                    new ContainerMetric(
                        "spinnaker-github-io",
                        ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "2Mi")),
                    new ContainerMetric(
                        "istio-proxy",
                        ImmutableMap.of("CPU(cores)", "32m", "MEMORY(bytes)", "30Mi")),
                    new ContainerMetric(
                        "istio-init", ImmutableMap.of("CPU(cores)", "0m", "MEMORY(bytes)", "0Mi"))))
            .build();

    for (String pod : expectedMetrics.keys()) {
      Optional<KubernetesPodMetric> podMetric =
          podMetrics.stream()
              .filter(metric -> metric.getPodName().equals(pod))
              .filter(metric -> metric.getNamespace().equals(NAMESPACE))
              .findAny();
      assertThat(podMetric.isPresent()).isTrue();
      assertThat(podMetric.get().getContainerMetrics())
          .containsExactlyInAnyOrderElementsOf(expectedMetrics.get(pod));
    }
  }

  @ParameterizedTest(name = "{index} ==> retries enabled = {0}")
  @ValueSource(booleans = {true, false})
  void kubectlJobExecutorRaisesException(boolean retriesEnabled) {
    when(jobExecutor.runJob(any(JobRequest.class)))
        .thenThrow(new JobExecutionException("unknown exception", new IOException()));

    if (retriesEnabled) {
      kubernetesConfigurationProperties.getJobExecutor().getRetries().setEnabled(true);
    }

    KubectlJobExecutor kubectlJobExecutor =
        new KubectlJobExecutor(
            jobExecutor, "kubectl", "oauth2l", kubernetesConfigurationProperties);

    JobExecutionException thrown =
        assertThrows(
            JobExecutionException.class,
            () -> kubectlJobExecutor.topPod(mockKubernetesCredentials(), "test", "test-pod"));

    if (retriesEnabled) {
      // should be called 3 times as there were max 3 attempts made
      verify(jobExecutor, times(3)).runJob(any(JobRequest.class));
    } else {
      verify(jobExecutor).runJob(any(JobRequest.class));
    }

    // at the end, with or without retries, the exception should still be thrown
    assertTrue(thrown.getMessage().contains("unknown exception"));
  }

  /** Returns a mock KubernetesCredentials object */
  private static KubernetesCredentials mockKubernetesCredentials() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getAccountName()).thenReturn("mock-account");
    when(credentials.getKubectlExecutable()).thenReturn("");
    return credentials;
  }
}
