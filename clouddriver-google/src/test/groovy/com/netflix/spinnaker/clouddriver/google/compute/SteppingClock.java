package com.netflix.spinnaker.clouddriver.google.compute;

import com.netflix.spectator.api.Clock;
import java.time.Duration;

class SteppingClock implements Clock {

  private long currentTimeMs = 0;
  private final int msAdjustmentBetweenCalls;

  public SteppingClock(int msAdjustmentBetweenCalls) {
    this.msAdjustmentBetweenCalls = msAdjustmentBetweenCalls;
  }

  @Override
  public long wallTime() {
    currentTimeMs += msAdjustmentBetweenCalls;
    return currentTimeMs;
  }

  @Override
  public long monotonicTime() {
    currentTimeMs += msAdjustmentBetweenCalls;
    return Duration.ofMillis(currentTimeMs).toNanos();
  }
}
