package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TencentInstanceHealth implements Health {
  private final String healthClass = "platform";
  private final String type = "Tencent";
  private Status instanceStatus;

  public HealthState getState() {
    return instanceStatus.toHealthState();
  }

  public static enum Status {
    PENDING,
    LAUNCH_FAILED,
    RUNNING,
    STOPPED,
    STARTING,
    STOPPING,
    REBOOTING,
    SHUTDOWN,
    TERMINATING;

    public HealthState toHealthState() {
      switch (this) {
        case PENDING:
          return HealthState.Starting;
        case RUNNING:
          return HealthState.Unknown;
        case STOPPED:
          return HealthState.Down;
        default:
          return HealthState.Unknown;
      }
    }
  }
}
