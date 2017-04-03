package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.model.JobStatus
import mesosphere.metronome.client.model.v1.GetJobResponse
import mesosphere.metronome.client.model.v1.JobRun
import mesosphere.metronome.client.model.v1.JobRunSummary
import org.joda.time.Instant

class DcosJobStatus implements JobStatus, Serializable {
  final String provider = DcosCloudProvider.ID

  GetJobResponse job
  JobRun jobRun
  JobRunSummary jobRunSummary
  String name
  String account
  String id
  String location
  Long createdTime
  Long completedTime
  boolean successful

  DcosJobStatus(GetJobResponse job, String id, String account) {
    this.name = job.id
    this.id = id
    this.location = job.id
    this.account = account
    this.job = job

    this.jobRun = job.activeRuns.find {
      jobRun -> jobRun.id == id
    }

    if (jobRun) {
      this.createdTime = Instant.parse(jobRun.createdAt).millis
      this.completedTime = null
      this.successful = false
    } else {
      this.jobRunSummary = (job.history.successfulFinishedRuns + job.history.failedFinishedRuns).find {
        jobSummary -> jobSummary.id == id
      }
    }

    if (jobRunSummary) {
      this.successful = job.history.successfulFinishedRuns.contains(this.jobRunSummary)
      this.createdTime = Instant.parse(jobRunSummary.createdAt).millis
      this.completedTime = Instant.parse(jobRunSummary.finishedAt).millis
    }
  }

  @Override
  Map<String, String> getCompletionDetails() {
    [
      successful  : successful.toString(),
      jobId     : name,
      taskId    : id
    ]
  }

  @Override
  JobState getJobState() {
    if (jobRun) {
      jobRun.createdAt ? JobState.Starting : JobState.Running
    } else if (jobRunSummary) {
      successful ? JobState.Succeeded : JobState.Failed
    } else {
      JobState.Unknown
    }
  }
}
