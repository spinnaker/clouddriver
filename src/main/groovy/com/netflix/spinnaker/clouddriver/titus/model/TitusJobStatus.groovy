/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.client.model.Job as TitusApiJob
import com.netflix.spinnaker.clouddriver.titus.client.model.Job.TaskSummary
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState

/**
 * Titus Job Status
 */
class TitusJobStatus implements com.netflix.spinnaker.clouddriver.model.JobStatus, Serializable {

  public static final String TYPE = Keys.PROVIDER

  String id
  String name
  String type = TYPE
  Map env
  String location
  Long createdTime
  Long completedTime
  String provider = 'titus'
  String account
  String cluster
  Instance instance
  String application
  String region
  Map<String, String> completionDetails = [:]

  JobState jobState

  TitusJobStatus(TitusApiJob job, String titusAccount, String titusRegion) {
    account = titusAccount
    region = titusRegion
    id = job.id
    name = job.name
    createdTime = job.submittedAt ? job.submittedAt.time : null
    application = Names.parseName(job.name).app
    TaskSummary task = job.tasks.last()
    jobState = convertTaskStateToJobState(task)
    completionDetails = convertCompletionDetails(task)
  }

  Map<String, String> convertCompletionDetails(TaskSummary task) {
    [
      message   : task.message,
      taskId    : task.id,
      instanceId: task.instanceId
    ]
  }

  JobState convertTaskStateToJobState(TaskSummary task) {
    switch (task.state) {
      case [TaskState.DEAD, TaskState.CRASHED, TaskState.FAILED]:
        JobState.Failed
        break
      case [TaskState.FINISHED, TaskState.STOPPED]:
        JobState.Succeeded
        break
      case [TaskState.STARTING, TaskState.QUEUED, TaskState.DISPATCHED]:
        JobState.Starting
        break
      default:
        JobState.Running
    }
  }

}
