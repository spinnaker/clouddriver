package com.netflix.spinnaker.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("artifacts")
@Slf4j
class ArtifactProviderProperties {
  long connectTimeoutMs;
  long readTimeoutMs;
}
