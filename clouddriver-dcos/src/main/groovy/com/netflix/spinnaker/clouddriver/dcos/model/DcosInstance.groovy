package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import mesosphere.marathon.client.model.v2.Task

import java.time.Instant

/**
 * Equivalent of a DCOS {@link mesosphere.marathon.client.model.v2.Task}
 */
class DcosInstance implements Instance, Serializable {

  Task task
  final String providerType = DcosCloudProvider.ID
  final String cloudProvider = DcosCloudProvider.ID

  String name
  String taskId
  Long launchTime
  String zone
  String json
  String host
  String state
  String account
  String dcosCluster
  List<Map<String, Object>> health

  DcosInstance() {}

  DcosInstance(Task task, String account, String cluster, boolean deploymentsActive) {
    this.task = task
    this.taskId = task.id
    this.name = task.id
    this.host = task.host
    this.state = task.state
    this.account = account
    this.dcosCluster = cluster

    this.json = task.toString()

    this.launchTime = task.startedAt ? Instant.parse(task.startedAt).toEpochMilli() : null
    this.health = [getTaskHealth(task, deploymentsActive)]

    // TODO Instance interfaces says this is the availability zone. Not sure we have this concept - we can only get the host.
    // Should this be our concept of region? Kubernetes uses namespace here.
    this.zone = DcosSpinnakerAppId.parse(task.appId, account, cluster).get().safeRegion

    // TODO
    // task.ports
    // task.servicePorts
    // task.ipAddresses
  }

  private static Map<String, String> getTaskHealth(Task task, boolean deploymentsActive) {

    def health = [:]

    health["healthClass"] = "platform"
    health["type"] = "MesosTask"

    // TODO investigate STAGING, GONE, and KILLING. In general validate these.
    switch (task.state) {
      case "TASK_RUNNING":
        // TODO Not sure if there could be a race condition where healthCheckResults are not populated for a period after TASK_RUNNING is set for the task.
        health["state"] = deploymentsActive || (task.healthCheckResults && task.healthCheckResults.any {
          !it.alive
        }) ? HealthState.Down : HealthState.Up
        break;
      case "TASK_STARTING":
        health["state"] = HealthState.Starting
        break;
      case "TASK_FINISHED":
        health["state"] = HealthState.Succeeded
        break
      case "TASK_FAILED":
      case "TASK_ERROR":
      case "TASK_DROPPED":
        health["state"] = HealthState.Failed
        break
      case "TASK_KILLED":
      case "TASK_STAGING":
      case "TASK_GONE":
        health["state"] = HealthState.Down
        break
      case "TASK_KILLING":
      case "TASK_UNREACHABLE":
      case "TASK_GONE_BY_OPERATOR":
      case "TASK_UNKNOWN":
      default:
        health["state"] = HealthState.Unknown
    }

    health
  }

  @Override
  HealthState getHealthState() {
    someUpRemainingUnknown(health) ? HealthState.Up :
            someSucceededRemainingUnknown(health) ? HealthState.Succeeded :
                    anyStarting(health) ? HealthState.Starting :
                            anyDown(health) ? HealthState.Down :
                                    anyFailed(health) ? HealthState.Failed :
                                            anyOutOfService(health) ? HealthState.OutOfService :
                                                    HealthState.Unknown
  }

  private static boolean anyDown(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Down.name() }
  }

  private static boolean someUpRemainingUnknown(List<Map<String, String>> healthsList) {
    List<Map<String, String>> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown.name() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Up.name() } : false
  }

  private static boolean someSucceededRemainingUnknown(List<Map<String, String>> healthsList) {
    List<Map<String, String>> knownHealthList = healthsList.findAll { it.state != HealthState.Unknown.name() }
    knownHealthList ? knownHealthList.every { it.state == HealthState.Succeeded.name() } : false
  }

  private static boolean anyStarting(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Starting.name() }
  }

  private static boolean anyFailed(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.Failed.name() }
  }

  private static boolean anyOutOfService(List<Map<String, String>> healthsList) {
    healthsList.any { it.state == HealthState.OutOfService.name() }
  }

  @Override
  boolean equals(Object o) {
    o instanceof DcosInstance ? o.name == name : false
  }

  @Override
  int hashCode() {
    return name.hashCode()
  }
}
