package com.netflix.spinnaker.clouddriver.deploy;

import java.util.List;

public class DefaultDeployHandlerRegistry implements DeployHandlerRegistry {

  private List<DeployHandler> deployHandlers;

  public DefaultDeployHandlerRegistry(List<DeployHandler> deployHandlers) {
    this.deployHandlers = deployHandlers;
  }

  @Override
  public DeployHandler findHandler(final DeployDescription description) {
    return deployHandlers.stream()
        .filter(it -> it.handles(description))
        .findFirst()
        .orElseThrow(DeployHandlerNotFoundException::new);
  }

  public List<DeployHandler> getDeployHandlers() {
    return deployHandlers;
  }

  public void setDeployHandlers(List<DeployHandler> deployHandlers) {
    this.deployHandlers = deployHandlers;
  }
}
