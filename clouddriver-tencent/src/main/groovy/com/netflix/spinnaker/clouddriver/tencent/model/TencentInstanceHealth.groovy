package com.netflix.spinnaker.clouddriver.tencent.model

import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.Canonical

@Canonical
class TencentInstanceHealth implements Health {

  final String healthClass = 'platform'
  final String type = 'Tencent'
  Status instanceStatus

  HealthState getState() {
    instanceStatus.toHealthState()
  }

  enum Status {
    PENDING,
    LAUNCH_FAILED,
    RUNNING,
    STOPPED,
    STARTING,
    STOPPING,
    REBOOTING,
    SHUTDOWN,
    TERMINATING

    HealthState toHealthState() {
      switch (this) {
        case PENDING:
          return HealthState.Starting
        case RUNNING:
          return HealthState.Unknown
        case STOPPED:
          return HealthState.Down
        default:
          return HealthState.Unknown
      }
    }
  }
}
