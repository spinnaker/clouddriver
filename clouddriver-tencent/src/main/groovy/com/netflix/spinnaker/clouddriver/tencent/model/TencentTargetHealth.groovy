package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState

class TencentTargetHealth implements Health {

  final String type = 'LoadBalancer'
  TargetHealthStatus targetHealthStatus

  List<LBHealthSummary> loadBalancers = []

  TencentTargetHealth(healthStatus) {
    targetHealthStatus = healthStatus ? TargetHealthStatus.HEALTHY : TargetHealthStatus.UNHEALTHY
  }

  TencentTargetHealth() {
    targetHealthStatus = TargetHealthStatus.UNKNOWN
  }

  HealthState getState() {
    targetHealthStatus.toHealthState()
  }

  enum TargetHealthStatus {
    UNHEALTHY,
    HEALTHY,
    UNKNOWN

    HealthState toHealthState() {
      switch (this) {
        case UNHEALTHY:
          return HealthState.Down
        case HEALTHY:
          return HealthState.Up
        case UNKNOWN:
          return HealthState.Down
        default:
          return HealthState.Unknown
      }
    }

    LBHealthSummary.ServiceStatus toServiceStatus() {
      switch (this) {
        case HEALTHY:
          return LBHealthSummary.ServiceStatus.InService
        default:
          return LBHealthSummary.ServiceStatus.OutOfService
      }
    }
  }

  static class LBHealthSummary {
    String loadBalancerName
    ServiceStatus state

    String getDescription() {
      state == ServiceStatus.OutOfService ?
        "Instance has failed at least the Unhealthy Threshold number of health checks consecutively." :
        "Healthy"
    }

    enum ServiceStatus {
      InService,
      OutOfService
    }
  }


}
