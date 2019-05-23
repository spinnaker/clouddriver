package com.netflix.spinnaker.clouddriver.google.compute;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoogleComputeApiFactory {

  private final GoogleOperationPoller operationPoller;
  private final Registry registry;

  @Autowired
  public GoogleComputeApiFactory(GoogleOperationPoller operationPoller, Registry registry) {
    this.operationPoller = operationPoller;
    this.registry = registry;
  }

  public GoogleServerGroupManagers createServerGroupManagers(
      GoogleNamedAccountCredentials credentials, GoogleServerGroup.View serverGroup) {
    return serverGroup.getRegional()
        ? new RegionGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getRegion())
        : new ZoneGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getZone());
  }

  public InstanceTemplates createInstanceTemplates(GoogleNamedAccountCredentials credentials) {
    return new InstanceTemplates(credentials, operationPoller, registry);
  }
}
