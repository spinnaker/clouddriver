package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.tencent.config.TencentConfigurationProperties;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("tencent.enabled")
@ComponentScan({"com.netflix.spinnaker.clouddriver.tencent"})
@Import({com.netflix.spinnaker.clouddriver.tencent.security.TencentCredentialsInitializer.class})
public class TencentConfiguration {
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("tencent")
  public TencentConfigurationProperties tencentConfigurationProperties() {
    return new TencentConfigurationProperties();
  }
}
