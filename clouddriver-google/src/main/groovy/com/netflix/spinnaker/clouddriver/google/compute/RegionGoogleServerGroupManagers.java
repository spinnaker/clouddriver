package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.RegionInstanceGroupManagersAbandonInstancesRequest;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeOperationRequestImpl.OperationWaiter;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Map;

final class RegionGoogleServerGroupManagers extends AbstractGoogleServerGroupManagers {

  private final Compute.RegionInstanceGroupManagers managers;
  private final String region;

  RegionGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller poller,
      Registry registry,
      String instanceGroupName,
      String region) {
    super(credentials, poller, registry, instanceGroupName);
    this.managers = credentials.getCompute().regionInstanceGroupManagers();
    this.region = region;
  }

  @Override
  ComputeRequest<Operation> performAbandonInstances(List<String> instances) throws IOException {

    RegionInstanceGroupManagersAbandonInstancesRequest request =
        new RegionInstanceGroupManagersAbandonInstancesRequest();
    request.setInstances(instances);
    return managers.abandonInstances(getProject(), region, getInstanceGroupName(), request);
  }

  @Override
  ComputeRequest<Operation> performDelete() throws IOException {
    return managers.delete(getProject(), region, getInstanceGroupName());
  }

  @Override
  ComputeRequest<InstanceGroupManager> performGet() throws IOException {
    return managers.get(getProject(), region, getInstanceGroupName());
  }

  @Override
  ComputeRequest<Operation> performUpdate(InstanceGroupManager content) throws IOException {
    return managers.update(getProject(), region, getInstanceGroupName(), content);
  }

  @Override
  OperationWaiter getOperationWaiter(
      GoogleNamedAccountCredentials credentials, GoogleOperationPoller poller) {
    return (operation, task, phase) ->
        poller.waitForRegionalOperation(
            credentials.getCompute(),
            credentials.getProject(),
            GCEUtil.getLocalName(operation.getRegion()),
            operation.getName(),
            /* timeoutSeconds= */ null,
            task,
            GCEUtil.getLocalName(operation.getTargetLink()),
            phase);
  }

  @Override
  String getManagersType() {
    return "regionInstanceGroupManagers";
  }

  @Override
  Map<String, String> getRegionOrZoneTags() {
    return ImmutableMap.of(
        GoogleExecutor.getTAG_SCOPE(),
        GoogleExecutor.getSCOPE_REGIONAL(),
        GoogleExecutor.getTAG_REGION(),
        region);
  }
}
