/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.netflix.spinnaker.clouddriver.titus.model.TitusSecurityGroup;
import com.netflix.titus.grpc.protogen.*;

import java.util.*;
import java.util.stream.Collectors;

public class Job {

  public static class TaskSummary {

    public TaskSummary() {
    }

    public TaskSummary(com.netflix.titus.grpc.protogen.Task grpcTask) {
      id = grpcTask.getId();
      state = TaskState.from(grpcTask.getStatus().getState().name(), grpcTask.getStatus().getReasonCode());
      instanceId = grpcTask.getTaskContextOrDefault("v2.taskInstanceId", id);
      host = grpcTask.getTaskContextOrDefault("agent.host", null);
      region = grpcTask.getTaskContextOrDefault("agent.region", null);
      zone = grpcTask.getTaskContextOrDefault("agent.zone", null);
      submittedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.Accepted);
      launchedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.Launched);
      startedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.StartInitiated);
      finishedAt = getTimestampFromStatus(grpcTask, TaskStatus.TaskState.Finished);
      containerIp = grpcTask.getTaskContextOrDefault("task.containerIp", null);
      logLocation = new HashMap<>();
      logLocation.put("ui", grpcTask.getLogLocation().getUi().getUrl());
      logLocation.put("liveStream", grpcTask.getLogLocation().getLiveStream().getUrl());
      HashMap<String, String> s3 = new HashMap<>();
      s3.put("accountId", grpcTask.getLogLocation().getS3().getAccountId());
      s3.put("accountName", grpcTask.getLogLocation().getS3().getAccountName());
      s3.put("region", grpcTask.getLogLocation().getS3().getRegion());
      s3.put("bucket", grpcTask.getLogLocation().getS3().getBucket());
      s3.put("key", grpcTask.getLogLocation().getS3().getKey());
      logLocation.put("s3", s3);
    }

    private Date getTimestampFromStatus(com.netflix.titus.grpc.protogen.Task grpcTask, TaskStatus.TaskState state) {
      return grpcTask.getStatusHistoryList().stream().filter(status -> status.getState().equals(state)).findFirst().map(status -> new Date(status.getTimestamp())).orElse(null);
    }

    private String id;
    private String instanceId;
    private TaskState state;
    private String host;
    private String region;
    private String zone;
    private Date submittedAt;
    private Date launchedAt;
    private Date startedAt;
    private Date finishedAt;
    private String message;
    private Map<String, Object> data;
    private String stdoutLive;
    private String logs;
    private String snapshots;
    private String containerIp;

    private Map<String, Object> logLocation;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getInstanceId() {
      return instanceId;
    }

    public void setInstanceId(String instanceId) {
      this.instanceId = instanceId;
    }

    public TaskState getState() {
      return state;
    }

    public void setState(TaskState state) {
      this.state = state;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getZone() {
      return zone;
    }

    public void setZone(String zone) {
      this.zone = zone;
    }

    public Date getSubmittedAt() {
      return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt) {
      this.submittedAt = submittedAt;
    }

    public Date getLaunchedAt() {
      return launchedAt;
    }

    public void setLaunchedAt(Date launchedAt) {
      this.launchedAt = launchedAt;
    }

    public Date getStartedAt() {
      return startedAt;
    }

    public void setStartedAt(Date startedAt) {
      this.startedAt = startedAt;
    }

    public Date getFinishedAt() {
      return finishedAt;
    }

    public void setFinishedAt(Date finishedAt) {
      this.finishedAt = finishedAt;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public Map<String, Object> getData() {
      return data;
    }

    public void setData(Map<String, Object> data) {
      this.data = data;
    }

    public String getStdoutLive() {
      return stdoutLive;
    }

    public void setStdoutLive(String stdoutLive) {
      this.stdoutLive = stdoutLive;
    }

    public String getLogs() {
      return logs;
    }

    public void setLogs(String logs) {
      this.logs = logs;
    }

    public String getSnapshots() {
      return snapshots;
    }

    public void setSnapshots(String snapshots) {
      this.snapshots = snapshots;
    }

    public String getContainerIp() {
      return containerIp;
    }

    public void setContainerIp(String containerIp) {
      this.containerIp = containerIp;
    }

    public Map<String, Object> getLogLocation() {
      return logLocation;
    }

  }

  private String id;
  private String name;
  private String type;
  private List<String> tags;
  private String applicationName;
  private String appName;
  private String user;
  private String version;
  private String entryPoint;
  private String iamProfile;
  private String capacityGroup;
  private Boolean inService;
  private int instances;
  private int instancesMin;
  private int instancesMax;
  private int instancesDesired;
  private int cpu;
  private int memory;
  private int disk;
  private int gpu;
  private int networkMbps;
  private int[] ports;
  private Map<String, String> environment;
  private int retries;
  private int runtimeLimitSecs;
  private boolean allocateIpAddress;
  private Date submittedAt;
  private List<TaskSummary> tasks;
  private Map<String, String> labels;
  private List<String> securityGroups;
  private String jobGroupStack;
  private String jobGroupDetail;
  private String jobGroupSequence;
  private List<String> hardConstraints;
  private List<String> softConstraints;
  private Efs efs;
  private MigrationPolicy migrationPolicy;
  private String jobState;

  public Job() {
  }

  public Job(com.netflix.titus.grpc.protogen.Job grpcJob, List<com.netflix.titus.grpc.protogen.Task> grpcTasks) {
    id = grpcJob.getId();

    if (grpcJob.getJobDescriptor().getJobSpecCase().getNumber() == JobDescriptor.BATCH_FIELD_NUMBER) {
      type = "batch";
      BatchJobSpec batchJobSpec = grpcJob.getJobDescriptor().getBatch();
      instancesMin = batchJobSpec.getSize();
      instancesMax = batchJobSpec.getSize();
      instancesDesired = batchJobSpec.getSize();
      instances = batchJobSpec.getSize();
      runtimeLimitSecs = (int) batchJobSpec.getRuntimeLimitSec();
      retries = batchJobSpec.getRetryPolicy().getImmediate().getRetries();
    }

    if (grpcJob.getJobDescriptor().getJobSpecCase().getNumber() == JobDescriptor.SERVICE_FIELD_NUMBER) {
      type = "service";
      ServiceJobSpec serviceSpec = grpcJob.getJobDescriptor().getService();
      inService = serviceSpec.getEnabled();
      instances = serviceSpec.getCapacity().getDesired();
      instancesMin = serviceSpec.getCapacity().getMin();
      instancesMax = serviceSpec.getCapacity().getMax();
      instancesDesired = serviceSpec.getCapacity().getDesired();
      migrationPolicy = new MigrationPolicy();
      com.netflix.titus.grpc.protogen.MigrationPolicy policy = serviceSpec.getMigrationPolicy();
      if (policy.getPolicyCase().equals(com.netflix.titus.grpc.protogen.MigrationPolicy.PolicyCase.SELFMANAGED)) {
        migrationPolicy.setType("selfManaged");
      } else {
        migrationPolicy.setType("systemDefault");
      }
    }

    labels = grpcJob.getJobDescriptor().getAttributesMap();
    user = grpcJob.getJobDescriptor().getOwner().getTeamEmail();

    if (grpcTasks != null) {
      tasks = grpcTasks.stream().map(grpcTask -> new TaskSummary(grpcTask)).collect(Collectors.toList());
    } else {
      tasks = new ArrayList<>();
    }

    appName = grpcJob.getJobDescriptor().getApplicationName();
    name = grpcJob.getJobDescriptor().getAttributesOrDefault("name", appName);
    applicationName = grpcJob.getJobDescriptor().getContainer().getImage().getName();
    version = grpcJob.getJobDescriptor().getContainer().getImage().getTag();
    entryPoint = grpcJob.getJobDescriptor().getContainer().getEntryPointList().stream().collect(Collectors.joining(" "));
    capacityGroup = grpcJob.getJobDescriptor().getCapacityGroup();
    cpu = (int) grpcJob.getJobDescriptor().getContainer().getResources().getCpu();
    memory = grpcJob.getJobDescriptor().getContainer().getResources().getMemoryMB();
    gpu = grpcJob.getJobDescriptor().getContainer().getResources().getGpu();
    networkMbps = grpcJob.getJobDescriptor().getContainer().getResources().getNetworkMbps();
    disk = grpcJob.getJobDescriptor().getContainer().getResources().getDiskMB();
    jobGroupSequence = grpcJob.getJobDescriptor().getJobGroupInfo().getSequence();
    jobGroupStack = grpcJob.getJobDescriptor().getJobGroupInfo().getStack();
    jobGroupDetail = grpcJob.getJobDescriptor().getJobGroupInfo().getDetail();
    environment = grpcJob.getJobDescriptor().getContainer().getEnvMap();
    securityGroups = grpcJob.getJobDescriptor().getContainer().getSecurityProfile().getSecurityGroupsList().stream().collect(Collectors.toList());
    iamProfile = grpcJob.getJobDescriptor().getContainer().getSecurityProfile().getIamRole();
    allocateIpAddress = true;
    submittedAt = new Date(grpcJob.getStatus().getTimestamp());
    softConstraints = new ArrayList<String>();
    softConstraints.addAll(grpcJob.getJobDescriptor().getContainer().getSoftConstraints().getConstraintsMap().keySet());
    hardConstraints = new ArrayList<String>();
    hardConstraints.addAll(grpcJob.getJobDescriptor().getContainer().getHardConstraints().getConstraintsMap().keySet());

    jobState = grpcJob.getStatus().getState().toString();

    if (grpcJob.getJobDescriptor().getContainer().getResources().getEfsMountsCount() > 0) {
      efs = new Efs();
      ContainerResources.EfsMount firstMount = grpcJob.getJobDescriptor().getContainer().getResources().getEfsMounts(0);
      efs.setEfsId(firstMount.getEfsId());
      efs.setMountPerm(firstMount.getMountPerm().toString());
      efs.setMountPoint(firstMount.getMountPoint());
      if (firstMount.getEfsRelativeMountPoint() != null) {
        efs.setEfsRelativeMountPoint(firstMount.getEfsRelativeMountPoint());
      }
    }

  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getApplicationName() {
    return applicationName;
  }

  public void setApplicationName(String applicationName) {
    this.applicationName = applicationName;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getEntryPoint() {
    return entryPoint;
  }

  public void setEntryPoint(String entryPoint) {
    this.entryPoint = entryPoint;
  }

  public int getInstances() {
    return instances;
  }

  public void setInstances(int instances) {
    this.instances = instances;
  }

  public int getInstancesMin() {
    return instancesMin;
  }

  public void setInstancesMin(int instancesMin) {
    this.instancesMin = instancesMin;
  }

  public int getInstancesMax() {
    return instancesMax;
  }

  public void setInstancesMax(int instancesMax) {
    this.instancesMax = instancesMax;
  }

  public int getInstancesDesired() {
    return instancesDesired;
  }

  public void setInstancesDesired(int instancesDesired) {
    this.instancesDesired = instancesDesired;
  }

  public int getCpu() {
    return cpu;
  }

  public void setCpu(int cpu) {
    this.cpu = cpu;
  }

  public int getMemory() {
    return memory;
  }

  public void setMemory(int memory) {
    this.memory = memory;
  }

  public int getDisk() {
    return disk;
  }

  public void setDisk(int disk) {
    this.disk = disk;
  }

  public void setGpu(int gpu) {
    this.gpu = gpu;
  }

  public int getGpu() {
    return gpu;
  }

  public int[] getPorts() {
    return ports;
  }

  public void setPorts(int[] ports) {
    this.ports = ports;
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  public int getRetries() {
    return retries;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public int getRuntimeLimitSecs() {
    return runtimeLimitSecs;
  }

  public void setRuntimeLimitSecs(int runtimeLimitSecs) {
    this.runtimeLimitSecs = runtimeLimitSecs;
  }

  public boolean isAllocateIpAddress() {
    return allocateIpAddress;
  }

  public void setAllocateIpAddress(boolean allocateIpAddress) {
    this.allocateIpAddress = allocateIpAddress;
  }

  public Date getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Date submittedAt) {
    this.submittedAt = submittedAt;
  }

  public List<TaskSummary> getTasks() {
    return tasks;
  }

  public void setTasks(List<TaskSummary> tasks) {
    this.tasks = tasks;
  }

  public String getIamProfile() {
    return iamProfile;
  }

  public void setIamProfile(String iamProfile) {
    this.iamProfile = iamProfile;
  }

  public String getCapacityGroup() {
    return capacityGroup;
  }

  public void setCapacityGroup(String capacityGroup) {
    this.capacityGroup = capacityGroup;
  }

  public Boolean isInService() {
    return inService;
  }

  public void setInService(Boolean inService) {
    this.inService = inService;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public String getJobGroupStack() {
    return jobGroupStack;
  }

  public void setJobGroupStack(String jobGroupStack) {
    this.jobGroupStack = jobGroupStack;
  }

  public String getJobGroupDetail() {
    return jobGroupDetail;
  }

  public void setJobGroupDetail(String jobGroupDetail) {
    this.jobGroupDetail = jobGroupDetail;
  }

  public String getJobGroupSequence() {
    return jobGroupSequence;
  }

  public void setJobGroupSequence(String jobGroupSequence) {
    this.jobGroupSequence = jobGroupSequence;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public List<String> getHardConstraints() {
    return hardConstraints;
  }

  public void setHardConstraints(List<String> hardConstraints) {
    this.hardConstraints = hardConstraints;
  }

  public List<String> getSoftConstraints() {
    return softConstraints;
  }

  public void setSoftConstraints(List<String> softConstraints) {
    this.softConstraints = softConstraints;
  }

  public int getNetworkMbps() {
    return networkMbps;
  }

  public void setNetworkMbps(int networkMbps) {
    this.networkMbps = networkMbps;
  }

  public Efs getEfs() {
    return efs;
  }

  public void setEfs(Efs efs) {
    this.efs = efs;
  }

  public MigrationPolicy getMigrationPolicy() {
    return migrationPolicy;
  }

  public void setMigrationPolicy(MigrationPolicy migrationPolicy) {
    this.migrationPolicy = migrationPolicy;
  }

  public String getJobState() { return jobState; }

}
