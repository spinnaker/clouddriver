/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.SerializeApplicationAtomicOperation

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.SerializeApplicationDescription.RestoreSnapshotDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleCluster
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor
import com.netflix.spinnaker.clouddriver.jobs.JobRequest
import com.netflix.spinnaker.clouddriver.jobs.JobStatus
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.beans.factory.annotation.Autowired

class RestoreSnapshotAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESTORE_SNAPSHOT"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RestoreSnapshotDescription description
  private final String applicationName
  private final String accountName
  private final Long snapshotTimestamp
  private String project
  private List applicationTags

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  JobExecutor jobExecutor

  RestoreSnapshotAtomicOperation(RestoreSnapshotDescription description) {
    this.description = description
    this.applicationName = description.applicationName
    this.accountName = description.accountName
    this.snapshotTimestamp = description.snapshotTimestamp
    this.applicationTags = []
  }

  /* curl -X POST -H "Content-Type: application/json" -d '[ { "restoreSnapshot": { "applicationName": "example", "credentials": "my-google-account" "snapshotTimestamp": "123456789"}} ]' localhost:7002/gce/ops */
  @Override
  Void operate(List priorOutputs) {
    def credentials = accountCredentialsRepository.getOne(this.accountName) as GoogleNamedAccountCredentials
    this.project = credentials.project

    // Make directory for terraform files
    File dir = new File("$applicationName-$accountName");
    if (!dir.mkdir()) {
      throw new IllegalStateException("Error creating directory $applicationName-$accountName")
    }

    task.updateStatus BASE_PHASE, "Importing state of server groups for the application ${this.applicationName} in account ${this.accountName}"
    googleClusterProvider.getClusters(applicationName, accountName).each { GoogleCluster.View cluster ->
      cluster.serverGroups.each { GoogleServerGroup.View serverGroup ->
        importServerGroupState(serverGroup)
      }
    }

    task.updateStatus BASE_PHASE, "Importing state of load balancers for the application ${this.applicationName} in account ${this.accountName}"
    googleLoadBalancerProvider.getApplicationLoadBalancers(applicationName).each { GoogleLoadBalancerView loadBalancer ->
      if (loadBalancer.account == this.accountName) {
        importLoadBalancerState(loadBalancer)
      }
    }

    task.updateStatus BASE_PHASE, "Importing state of security groups for application ${this.applicationName} in account ${this.accountName}"
    googleSecurityGroupProvider.getAll(true).each { GoogleSecurityGroup securityGroup ->
      if (securityGroup.accountName == this.accountName && securityGroup.targetTags && !Collections.disjoint(securityGroup.targetTags, applicationTags)) {
        importSecurityGroupState(securityGroup)
      }
    }

    cleanUpDirectory()
    return null
  }

  private Void importServerGroupState(GoogleServerGroup.View serverGroup) {
    importResource("google_compute_instance_group_manager", serverGroup.name)
    def instanceTemplate = serverGroup.imageSummary.image
    if (instanceTemplate) {
      importResource("google_compute_instance_template", serverGroup.imageSummary.image.name)
      if (instanceTemplate.properties.tags?.items) {
        applicationTags.addAll(instanceTemplate.properties.tags.items)
      }
    }
    if (serverGroup.autoscalingPolicy) {
      importResource("google_compute_autoscaler", serverGroup.name)
    }
    return null
  }

  private Void importLoadBalancerState(GoogleLoadBalancerView loadBalancer) {
    def targetPoolName = loadBalancer.targetPool.split("/").last()
    importResource("google_compute_target_pool", targetPoolName)
    importResource("google_compute_forwarding_rule", loadBalancer.name)
    if (loadBalancer.healthCheck) {
      importResource("google_compute_http_health_check", loadBalancer.healthCheck.name)
    }
    return null
  }

  private Void importSecurityGroupState(GoogleSecurityGroup securityGroup) {
    importResource("google_compute_firewall", securityGroup.name)
    return null
  }

  private void importResource(String resource, String name) {
    ArrayList<String> command = ["terraform", "import", "-state=$applicationName-$accountName/terraform.tfstate", "$resource.$name", name]
    String jobId = jobExecutor.startJob(new JobRequest(tokenizedCommand: command))
    waitForJobCompletion(jobId)
  }

  private void waitForJobCompletion(String jobId) {
    sleep(1000)
    JobStatus jobStatus = jobExecutor.updateJob(jobId)
    while (jobStatus.state == JobStatus.State.RUNNING) {
      sleep(1000)
      jobStatus = jobExecutor.updateJob(jobId)
    }

    if (jobStatus.result == JobStatus.Result.FAILURE && jobStatus.logsContent) {
      cleanUpDirectory()
      throw new IllegalArgumentException(jobStatus.logsContent)
    }
  }

  private void cleanUpDirectory() {
    List<File> files = new ArrayList<>()
    files << new File("$applicationName-$accountName/terraform.tfstate")
    files << new File("$applicationName-$accountName/terraform.tfstate.backup")
    for (File file: files) {
      if (file.exists()) {
        if (!file.delete()) {
          throw new IllegalStateException("Error deleting file $file.name")
        }
      }
    }
    def dir = new File("$applicationName-$accountName")
    if (!dir.delete()) {
      throw new IllegalStateException("Error deleting directory $dir.name")
    }
  }
}
