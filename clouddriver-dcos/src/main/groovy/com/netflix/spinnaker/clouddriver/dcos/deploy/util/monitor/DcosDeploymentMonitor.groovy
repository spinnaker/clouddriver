package com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor

import com.netflix.spinnaker.clouddriver.data.task.Task
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

/**
 * Methods for monitoring the state of a DCOS deployment
 */
interface DcosDeploymentMonitor {

  /**
   * @param dcosClient a DCOS client instance (cannot be null).
   * @param marathonApp The Marathon application to monitor (cannot be null).
   * @param deploymentId The corresponding deployment id to monitor (cannot be null).
   * @param timeoutSeconds The timeout interval in seconds (may be null).
   * @param task The task to be updated (may be null).
   * @param basePhase The phase
   * @return DcosDeploymentResult indicating the success or failure of the deployment
   * @throws com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException if the timeout interval elapses.
   */
  DcosDeploymentResult waitForAppDeployment(DCOS dcosClient, App marathonApp, String deploymentId,
                                            Long timeoutSeconds, Task task, String basePhase)

  /**
   * @param dcosClient a DCOS client instance (cannot be null).
   * @param marathonApp The Marathon application to monitor (cannot be null).
   * @param timeoutSeconds The timeout interval in seconds (may be null).
   * @param task The task to be updated (may be null).
   * @param basePhase The phase
   * @throws com.netflix.spinnaker.clouddriver.exceptions.OperationTimedOutException if the timeout interval elapses.
   */
  void waitForAppDestroy(DCOS dcosClient, App marathonApp,
                         Long timeoutSeconds, Task task, String basePhase)


  static class DcosDeploymentResult {
    boolean success
    Optional<App> deployedApp
  }
}
