/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.client.model.*;
import com.netflix.spinnaker.clouddriver.titus.client.model.HealthStatus;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.Task;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.titus.grpc.protogen.*;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RegionScopedTitusClient implements TitusClient {

  /**
   * Default connect timeout in milliseconds
   */
  private static final long DEFAULT_CONNECT_TIMEOUT = 60000;

  /**
   * Default read timeout in milliseconds
   */
  private static final long DEFAULT_READ_TIMEOUT = 20000;

  /**
   * An instance of {@link TitusRegion} that this RegionScopedTitusClient will use
   */
  private final TitusRegion titusRegion;

  private final Registry registry;

  private final List<TitusJobCustomizer> titusJobCustomizers;

  private final String environment;

  private final ObjectMapper objectMapper;

  private final JobManagementServiceGrpc.JobManagementServiceBlockingStub grpcBlockingStub;

  private final RetrySupport retrySupport;

  public RegionScopedTitusClient(TitusRegion titusRegion, Registry registry, List<TitusJobCustomizer> titusJobCustomizers, String environment, String eurekaName, GrpcChannelFactory grpcChannelFactory, RetrySupport retrySupport) {
    this(titusRegion, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, TitusClientObjectMapper.configure(), registry, titusJobCustomizers, environment, eurekaName, grpcChannelFactory, retrySupport);
  }

  public RegionScopedTitusClient(TitusRegion titusRegion,
                                 long connectTimeoutMillis,
                                 long readTimeoutMillis,
                                 ObjectMapper objectMapper,
                                 Registry registry,
                                 List<TitusJobCustomizer> titusJobCustomizers,
                                 String environment,
                                 String eurekaName,
                                 GrpcChannelFactory channelFactory,
                                 RetrySupport retrySupport
  ) {
    this.titusRegion = titusRegion;
    this.registry = registry;
    this.titusJobCustomizers = titusJobCustomizers;
    this.environment = environment;
    this.objectMapper = objectMapper;
    this.retrySupport = retrySupport;

    String titusHost = "";
    try {
      URL titusUrl = new URL(titusRegion.getEndpoint());
      titusHost = titusUrl.getHost();
    } catch (Exception e) {

    }
    this.grpcBlockingStub = JobManagementServiceGrpc.newBlockingStub(channelFactory.build(titusRegion, environment, eurekaName, DEFAULT_CONNECT_TIMEOUT, registry));

    if (!titusRegion.getFeatureFlags().isEmpty()) {
      log.info("Experimental Titus V3 client feature flags {} enabled for account {} and region {}",
        StringUtils.join(titusRegion.getFeatureFlags(), ","),
        titusRegion.getAccount(),
        titusRegion.getName());
    }
  }

  // APIs
  // ------------------------------------------------------------------------------------------

  @Override
  public Job getJobAndAllRunningAndCompletedTasks(String jobId) {
    return new Job(grpcBlockingStub.findJob(JobId.newBuilder().setId(jobId).build()), getTasks(Arrays.asList(jobId), true).get(jobId));
  }

  @Override
  public Job findJobByName(String jobName, boolean includeTasks) {
    JobQuery.Builder jobQuery = JobQuery.newBuilder()
      .putFilteringCriteria("jobType", "SERVICE")
      .putFilteringCriteria("attributes", "source:spinnaker,name:" + jobName)
      .putFilteringCriteria("attributes.op", "and");
    List<Job> results = getJobs(jobQuery, includeTasks);
    return results.isEmpty() ? null : results.get(0);
  }

  @Override
  public Job findJobByName(String jobName) {
    return findJobByName(jobName, false);
  }

  @Override
  public List<Job> findJobsByApplication(String application) {
    JobQuery.Builder jobQuery = JobQuery.newBuilder().putFilteringCriteria("appName", application);
    return getJobs(jobQuery, false);
  }

  @Override
  public String submitJob(SubmitJobRequest submitJobRequest) {
    JobDescription jobDescription = submitJobRequest.getJobDescription();
    if (jobDescription.getType() == null) {
      jobDescription.setType("service");
    }
    if (jobDescription.getUser() == null) {
      jobDescription.setUser("spinnaker@netflix.com");
    } else if (!jobDescription.getUser().contains("@")) {
      jobDescription.setUser(jobDescription.getUser() + "@netflix.com");
    }
    if (jobDescription.getJobGroupSequence() == null && jobDescription.getType().equals("service")) {
      try {
        int sequence = Names.parseName(jobDescription.getName()).getSequence();
        jobDescription.setJobGroupSequence(String.format("v%03d", sequence));
      } catch (Exception e) {
        // fail silently if we can't get a job group sequence
      }
    }
    jobDescription.getLabels().put("name", jobDescription.getName());
    jobDescription.getLabels().put("source", "spinnaker");
    jobDescription.getLabels().put("spinnakerAccount", submitJobRequest.getCredentials());
    for (TitusJobCustomizer customizer : titusJobCustomizers) {
      customizer.customize(jobDescription);
    }
    return TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).createJob(jobDescription.getGrpcJobDescriptor()).getId();
  }

  @Override
  public Task getTask(String taskId) {
    // new Task(grpcBlockingStub.findTask(taskId));
    // return new Task(grpcBlockingStub.findTask(com.netflix.titus.grpc.protogen.TaskId.newBuilder().setId(taskId).build()));
    return null;
  }

  @Override
  public void resizeJob(ResizeJobRequest resizeJobRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).updateJobCapacity(JobCapacityUpdate.newBuilder()
      .setJobId(resizeJobRequest.getJobId())
      .setCapacity(Capacity.newBuilder()
        .setDesired(resizeJobRequest.getInstancesDesired())
        .setMax(resizeJobRequest.getInstancesMax())
        .setMin(resizeJobRequest.getInstancesMin())
      )
      .build()
    );
  }

  @Override
  public void activateJob(ActivateJobRequest activateJobRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).updateJobStatus(JobStatusUpdate.newBuilder().setId(activateJobRequest.getJobId()).setEnableStatus(activateJobRequest.getInService()).build());
  }

  @Override
  public void setAutoscaleEnabled(String jobId, boolean shouldEnable) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).updateJobProcesses(
      JobProcessesUpdate.newBuilder()
        .setServiceJobProcesses(
          ServiceJobSpec.ServiceJobProcesses.newBuilder()
            .setDisableDecreaseDesired(!shouldEnable)
            .setDisableIncreaseDesired(!shouldEnable)
            .build()
        )
        .setJobId(jobId)
        .build()
    );
  }

  @Override
  public void terminateJob(TerminateJobRequest terminateJobRequest) {
    TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).killJob(JobId.newBuilder().setId(terminateJobRequest.getJobId()).build());
  }

  @Override
  public void terminateTasksAndShrink(TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob) {
    List<String> failedTasks = new ArrayList<>();
    terminateTasksAndShrinkJob.getTaskIds().forEach(id -> {
        try {
          killTaskWithRetry(id, terminateTasksAndShrinkJob);
        } catch (Exception e) {
          failedTasks.add(id);
          log.error("Failed to terminate and shrink titus task {} in account {} and region {}", id, titusRegion.getAccount(), titusRegion.getName(), e);
        }
      }
    );
    if (!failedTasks.isEmpty()) {
      throw new TitusException("Failed to terminate and shrink titus tasks: " + StringUtils.join(failedTasks, ","));
    }
  }

  private void killTaskWithRetry(String id, TerminateTasksAndShrinkJobRequest terminateTasksAndShrinkJob) {
    try {
      TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).killTask(TaskKillRequest.newBuilder().setTaskId(id).setShrink(terminateTasksAndShrinkJob.isShrink()).build());
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus() == Status.NOT_FOUND) {
        log.warn("Titus task {} not found, continuing with terminate tasks and shrink job request.", id);
      } else {
        retrySupport.retry(() ->
            TitusClientAuthenticationUtil.attachCaller(grpcBlockingStub).killTask(TaskKillRequest.newBuilder().setTaskId(id).setShrink(terminateTasksAndShrinkJob.isShrink()).build())
          , 2, 1000, false);
      }
    }
  }

  @Override
  public Map logsDownload(String taskId) {
    return null;
  }

  @Override
  public TitusHealth getHealth() {
    return new TitusHealth(HealthStatus.HEALTHY);
  }

  @Override
  public List<Job> getAllJobs() {
    JobQuery.Builder jobQuery = JobQuery.newBuilder()
      .putFilteringCriteria("jobType", "SERVICE")
      .putFilteringCriteria("attributes", "source:spinnaker");
    return getJobs(jobQuery);
  }

  private List<Job> getJobs(JobQuery.Builder jobQuery) {
    return getJobs(jobQuery, true);
  }

  private List<Job> getJobs(JobQuery.Builder jobQuery, boolean includeTasks) {
    List<Job> jobs = new ArrayList<>();
    List<com.netflix.titus.grpc.protogen.Job> grpcJobs = new ArrayList<>();
    String cursor = "";
    boolean hasMore;
    do {
      Page.Builder jobPage = Page.newBuilder().setPageSize(1000);
      if (!cursor.isEmpty()) {
        jobPage.setCursor(cursor);
      }
      jobQuery.setPage(jobPage);
      JobQuery criteria = jobQuery.build();
      JobQueryResult resultPage = TitusClientCompressionUtil.attachCaller(grpcBlockingStub).findJobs(criteria);
      grpcJobs.addAll(resultPage.getItemsList());
      cursor = resultPage.getPagination().getCursor();
      hasMore = resultPage.getPagination().getHasMore();
    } while (hasMore);

    final Map<String, List<com.netflix.titus.grpc.protogen.Task>> tasks;

    if (includeTasks) {
      List<String> jobIds = Collections.emptyList();
      if (!titusRegion.getFeatureFlags().contains("jobIds")) {
        jobIds = grpcJobs.stream().map(grpcJob -> grpcJob.getId()).collect(
          Collectors.toList()
        );
      }
      tasks = getTasks(jobIds, false);
    } else {
      tasks = Collections.emptyMap();
    }

    return grpcJobs.stream().map(grpcJob -> new Job(grpcJob, tasks.get(grpcJob.getId()))).collect(Collectors.toList());
  }

  private Map<String, List<com.netflix.titus.grpc.protogen.Task>> getTasks(List<String> jobIds, boolean includeDoneJobs) {
    List<com.netflix.titus.grpc.protogen.Task> tasks = new ArrayList<>();
    TaskQueryResult taskResults;
    String cursor = "";
    boolean hasMore;
    do {
      Page.Builder taskPage = Page.newBuilder().setPageSize(1000);
      if (!cursor.isEmpty()) {
        taskPage.setCursor(cursor);
      }
      TaskQuery.Builder taskQueryBuilder = TaskQuery.newBuilder();
      taskQueryBuilder.setPage(taskPage);
      if (!jobIds.isEmpty()) {
        taskQueryBuilder.putFilteringCriteria("jobIds", jobIds.stream().collect(Collectors.joining(",")));
      }
      if (titusRegion.getFeatureFlags().contains("jobIds")) {
        taskQueryBuilder.putFilteringCriteria("attributes", "source:spinnaker");
      }
      String filterByStates = "Accepted,Launched,StartInitiated,Started";
      if (includeDoneJobs) {
        filterByStates = filterByStates + ",KillInitiated,Finished";
      }
      taskQueryBuilder.putFilteringCriteria("taskStates", filterByStates);
      taskResults = TitusClientCompressionUtil.attachCaller(grpcBlockingStub).findTasks(
        taskQueryBuilder.build()
      );
      tasks.addAll(taskResults.getItemsList());
      cursor = taskResults.getPagination().getCursor();
      hasMore = taskResults.getPagination().getHasMore();
    } while (hasMore);
    return tasks.stream().collect(Collectors.groupingBy(task -> task.getJobId()));
  }

}
