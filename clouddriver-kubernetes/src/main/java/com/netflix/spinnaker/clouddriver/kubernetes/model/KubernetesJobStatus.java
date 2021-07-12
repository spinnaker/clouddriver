/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.model.JobState;
import com.netflix.spinnaker.clouddriver.model.JobStatus;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class KubernetesJobStatus implements JobStatus {

  String name;
  String cluster;
  String account;
  String id;
  String location;
  String provider = "kubernetes";
  Long createdTime;
  Long completedTime;
  String message;
  String reason;
  Integer exitCode;
  Integer signal;
  String summary;
  String logs;
  @JsonIgnore V1Job job;
  List<PodStatus> pods;
  String mostRecentPodName;

  public KubernetesJobStatus(V1Job job, String account) {
    this.job = job;
    this.account = account;
    this.name = job.getMetadata().getName();
    this.location = job.getMetadata().getNamespace();
    this.createdTime = job.getMetadata().getCreationTimestamp().getMillis();
  }

  @Override
  public Map<String, String> getCompletionDetails() {
    Map<String, String> details = new HashMap<>();
    details.put("exitCode", this.exitCode != null ? this.exitCode.toString() : "");
    details.put("signal", this.signal != null ? this.signal.toString() : "");
    details.put("message", this.message != null ? this.message : "");
    details.put("reason", this.reason != null ? this.reason : "");
    details.put("summary", this.summary != null ? this.summary : "");
    return details;
  }

  @Override
  public JobState getJobState() {
    V1JobStatus status = job.getStatus();
    if (status == null) {
      return JobState.Running;
    }
    int completions = Optional.of(job.getSpec()).map(V1JobSpec::getCompletions).orElse(1);
    int succeeded = Optional.of(status).map(V1JobStatus::getSucceeded).orElse(0);

    if (succeeded < completions) {
      Optional<V1JobCondition> condition = getFailedJobCondition(status);
      return condition.isPresent() ? JobState.Failed : JobState.Running;
    }
    return JobState.Succeeded;
  }

  private boolean jobFailed(V1JobCondition condition) {
    return "Failed".equalsIgnoreCase(condition.getType())
        && "True".equalsIgnoreCase(condition.getStatus());
  }

  private Optional<V1JobCondition> getFailedJobCondition(V1JobStatus status) {
    List<V1JobCondition> conditions = status.getConditions();
    conditions = conditions != null ? conditions : ImmutableList.of();
    return conditions.stream().filter(this::jobFailed).findFirst();
  }

  public void jobFailureDetails() {
    V1JobStatus status = this.job.getStatus();
    if (status != null) {
      Optional<V1JobCondition> condition = getFailedJobCondition(status);
      if (condition.isPresent()) {
        this.setMessage(condition.get().getMessage());
        this.setReason(condition.get().getReason());
      }
      // save all container outputs
      this.getPods().forEach(KubernetesJobStatus.PodStatus::updatePodStatus);

      // construct a meaningful message to explain why the job failed
      for (PodStatus pod : pods) {
        Optional<ContainerExecutionDetails> failedContainerDetails =
            pod.getContainerExecutionDetails().stream()
                .filter(
                    containerExecutionDetails ->
                        containerExecutionDetails
                                .getState()
                                .equals(V1ContainerState.SERIALIZED_NAME_TERMINATED)
                            && containerExecutionDetails.getExitCode() != null
                            && !containerExecutionDetails.getExitCode().equals("0"))
                .findFirst();

        if (failedContainerDetails.isEmpty()) {
          failedContainerDetails =
              pod.getContainerExecutionDetails().stream()
                  .filter(
                      containerExecutionDetails ->
                          containerExecutionDetails
                              .getState()
                              .equals(V1ContainerState.SERIALIZED_NAME_WAITING))
                  .findFirst();
        }

        if (failedContainerDetails.isPresent()) {
          ContainerExecutionDetails failedContainer = failedContainerDetails.get();
          this.summary =
              "Pod: "
                  + pod.getName()
                  + " had errors.\n  Container: "
                  + failedContainer.getName()
                  + " exited with code: "
                  + failedContainer.getExitCode()
                  + ".\n Status: "
                  + failedContainer.getStatus()
                  + ".\n Logs: "
                  + failedContainer.getLogs();
          break;
        }
      }
    }
  }

  @Data
  public static class PodStatus {
    private String name;
    private V1PodStatus status;
    private List<ContainerExecutionDetails> containerExecutionDetails;

    public PodStatus(V1Pod pod) {
      this.name = pod.getMetadata().getName();
      this.status = pod.getStatus();
      this.containerExecutionDetails = new ArrayList<>();
    }

    public void updatePodStatus() {
      if (this.status != null) {
        this.containerExecutionDetails.addAll(
            getContainerDetails(this.status.getInitContainerStatuses()));
        this.containerExecutionDetails.addAll(
            getContainerDetails(this.status.getContainerStatuses()));
      }
    }

    private static List<ContainerExecutionDetails> getContainerDetails(
        List<V1ContainerStatus> containerStatuses) {
      return Optional.ofNullable(containerStatuses).orElseGet(Collections::emptyList).stream()
          .filter(status -> status.getState() != null)
          .map(status -> new ContainerExecutionDetails(status.getName(), status.getState()))
          .collect(Collectors.toList());
    }
  }

  @Data
  @AllArgsConstructor
  public static class ContainerExecutionDetails {
    private String name;
    private String logs;
    private String status;
    private String exitCode;
    private String state;

    public ContainerExecutionDetails() {
      this.name = "";
      this.logs = "";
      this.status = "";
      this.exitCode = "";
      this.state = V1ContainerState.SERIALIZED_NAME_RUNNING;
    }

    public ContainerExecutionDetails(String name, V1ContainerState containerState) {
      this();

      this.name = name;
      if (containerState.getTerminated() != null) {
        V1ContainerStateTerminated terminatedContainerState = containerState.getTerminated();
        this.logs = terminatedContainerState.getMessage();
        this.status = terminatedContainerState.getReason();
        this.exitCode =
            terminatedContainerState.getExitCode() != null
                ? terminatedContainerState.getExitCode().toString()
                : "";
        this.state = V1ContainerState.SERIALIZED_NAME_TERMINATED;
      } else if (containerState.getWaiting() != null) {
        V1ContainerStateWaiting waitingContainerState = containerState.getWaiting();
        this.logs = waitingContainerState.getMessage();
        this.status = waitingContainerState.getReason();
        this.state = V1ContainerState.SERIALIZED_NAME_WAITING;
      } else {
        this.logs = "container is still in running state";
      }
    }
  }
}
