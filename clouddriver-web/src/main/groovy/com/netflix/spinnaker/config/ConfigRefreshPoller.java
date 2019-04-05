package com.netflix.spinnaker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class ConfigRefreshPoller {

  private ContextRefresher contextRefresher;

  public ConfigRefreshPoller(ContextRefresher contextRefresher) {
    this.contextRefresher = contextRefresher;
  }

  @Scheduled(fixedDelayString = "${refreshPollingDelay:20000}")
  public void refresh() {
    Set<String> keys = contextRefresher.refresh();
    log.info("Refreshed configuration for the following keys: " + keys);
  }
}
