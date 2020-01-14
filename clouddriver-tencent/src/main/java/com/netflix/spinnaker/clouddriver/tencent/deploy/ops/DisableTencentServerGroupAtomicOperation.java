package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription;

public class DisableTencentServerGroupAtomicOperation extends AbstractEnableDisableAtomicOperation {
  public DisableTencentServerGroupAtomicOperation(
      EnableDisableTencentServerGroupDescription description) {
    super(description);
  }

  public final String getBasePhase() {
    return basePhase;
  }

  public boolean getDisable() {
    return disable;
  }

  public boolean isDisable() {
    return disable;
  }

  public void setDisable(boolean disable) {
    this.disable = disable;
  }

  private final String basePhase = "DISABLE_SERVER_GROUP";
  private boolean disable = true;
}
