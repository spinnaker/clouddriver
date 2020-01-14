package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TencentTargetHealth implements Health {

  private final String type = "LoadBalancer";
  private TargetHealthStatus targetHealthStatus;

  private List<LBHealthSummary> loadBalancers = new ArrayList<>();

  public TencentTargetHealth(boolean healthStatus) {
    targetHealthStatus = healthStatus ? TargetHealthStatus.HEALTHY : TargetHealthStatus.UNHEALTHY;
  }

  public TencentTargetHealth() {
    targetHealthStatus = TargetHealthStatus.UNKNOWN;
  }

  public HealthState getState() {
    return targetHealthStatus.toHealthState();
  }

  public enum TargetHealthStatus {
    UNHEALTHY,
    HEALTHY,
    UNKNOWN;

    public HealthState toHealthState() {
      switch (this) {
        case UNHEALTHY:
          return HealthState.Down;
        case HEALTHY:
          return HealthState.Up;
        case UNKNOWN:
          return HealthState.Down;
        default:
          return HealthState.Unknown;
      }
    }

    public LBHealthSummary.ServiceStatus toServiceStatus() {
      switch (this) {
        case HEALTHY:
          return LBHealthSummary.ServiceStatus.InService;
        default:
          return LBHealthSummary.ServiceStatus.OutOfService;
      }
    }
  }

  @Data
  public static class LBHealthSummary {
    private String loadBalancerName;
    private ServiceStatus state;

    public String getDescription() {
      return state == ServiceStatus.OutOfService ?
        "Instance has failed at least the Unhealthy Threshold number of health checks consecutively." :
        "Healthy";
    }

    public enum ServiceStatus {
      InService,
      OutOfService
    }
  }


}
