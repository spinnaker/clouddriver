package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription;

public class EnableTencentServerGroupAtomicOperation extends AbstractEnableDisableAtomicOperation {
  public EnableTencentServerGroupAtomicOperation(
      EnableDisableTencentServerGroupDescription description) {
    super(description);
  }

  public final String getBasePhase() {
    return basePhase;
  }

  public final boolean getDisable() {
    return disable;
  }

  public final boolean isDisable() {
    return disable;
  }

  private final String basePhase = "ENABLE_SERVER_GROUP";
  private final boolean disable = false;
}
