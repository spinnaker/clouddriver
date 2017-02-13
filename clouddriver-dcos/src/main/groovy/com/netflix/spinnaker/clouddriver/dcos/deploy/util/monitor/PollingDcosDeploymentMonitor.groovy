package com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App

import static com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor.DcosDeploymentResult

class PollingDcosDeploymentMonitor implements DcosDeploymentMonitor {

  private final OperationPoller operationPoller;

  PollingDcosDeploymentMonitor(final OperationPoller operationPoller) {
    this.operationPoller = operationPoller;
  }

  @Override
  DcosDeploymentResult waitForAppDeployment(DCOS dcosClient, App marathonApp, String deploymentId, Long timeoutSeconds, Task task, String basePhase) {

    // Wait for the deployment to complete. Either the App will not return (404), meaning the deployment failed, or
    // the deployment id will be removed from the apps deployment list, meaning the deployment succeeded.
    Optional<App> maybeApp = operationPoller.waitForOperation(
            { dcosClient.maybeApp(marathonApp.id) },
            { Optional<App> retrievedApp -> !retrievedApp.isPresent() || !retrievedApp.get().deployments.find {it.id == deploymentId }},
            timeoutSeconds, task, marathonApp.id, basePhase) as Optional<App>

    new DcosDeploymentResult(success: maybeApp.isPresent(), deployedApp: maybeApp)
  }

  @Override
  void waitForAppDestroy(DCOS dcosClient, App marathonApp, Long timeoutSeconds, Task task, String basePhase) {
    operationPoller.waitForOperation(
            { dcosClient.maybeApp(marathonApp.id) },
            { Optional<App> retrievedApp -> !retrievedApp.isPresent() || retrievedApp.get().version != marathonApp.version },
            timeoutSeconds, task, marathonApp.id, basePhase)
  }
}
