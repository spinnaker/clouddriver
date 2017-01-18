//package com.netflix.spinnaker.clouddriver.dcos.model
//
//import com.netflix.frigga.Names
//import com.netflix.spinnaker.clouddriver.data.task.TaskState
//import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
//import com.netflix.spinnaker.clouddriver.model.Instance
//import com.netflix.spinnaker.clouddriver.model.JobState
//
///**
// * Dcos Job Status
// */
//class DcosJobStatus implements com.netflix.spinnaker.clouddriver.model.JobStatus, Serializable {
//
//  public static final String TYPE = Keys.PROVIDER
//
//  String id
//  String name
//  String type = TYPE
//  Map env
//  String location
//  Long createdTime
//  Long completedTime
//  String provider = 'titus'
//  String account
//  String cluster
//  Instance instance
//  String application
//  String region
//  Map<String, String> completionDetails = [:]
//
//  JobState jobState
//
//  DcosJobStatus(Job job, String titusAccount, String titusRegion) {
//    account = titusAccount
//    region = titusRegion
//    id = job.id
//    name = job.name
//    createdTime = job.submittedAt ? job.submittedAt.time : null
//    application = Names.parseName(job.name).app
//    TaskSummary task = job.tasks.last()
//    jobState = convertTaskStateToJobState(task)
//    completionDetails = convertCompletionDetails(task)
//  }
//
//  Map<String, String> convertCompletionDetails(TaskSummary task) {
//    [
//      message   : task.message,
//      taskId    : task.id,
//      instanceId: task.instanceId
//    ]
//  }
//
//  JobState convertTaskStateToJobState(TaskSummary task) {
//    switch (task.state) {
//      case [TaskState.DEAD, TaskState.CRASHED, TaskState.FAILED]:
//        JobState.Failed
//        break
//      case [TaskState.FINISHED, TaskState.STOPPED]:
//        JobState.Succeeded
//        break
//      case [TaskState.STARTING, TaskState.QUEUED, TaskState.DISPATCHED]:
//        JobState.Starting
//        break
//      default:
//        JobState.Running
//    }
//  }
//
//}
