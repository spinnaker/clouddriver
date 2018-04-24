/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorder;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.plugins.EC2Plugin;
import com.amazonaws.xray.plugins.ECSPlugin;
import com.amazonaws.xray.strategy.LogErrorContextMissingStrategy;
import com.amazonaws.xray.strategy.sampling.AllSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import com.amazonaws.xray.strategy.sampling.SamplingStrategy;
import com.netflix.spinnaker.clouddriver.aws.tracing.RedisDynamicSamplingStrategy;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;

@Configuration
@ConditionalOnExpression("${aws.enabled} && ${aws.xray.enabled}")
@EnableConfigurationProperties(AwsXrayConfigurationProperties.class)
public class AwsXrayConfiguration {

  @Bean
  public Filter xrayTracingFilter() {
    return new AWSXRayServletFilter();
  }

  @Bean
  public SamplingStrategy samplingStrategy(RedisClientSelector redisClientSelector,
                                           AwsXrayConfigurationProperties properties) {
    return new RedisDynamicSamplingStrategy(redisClientSelector, properties.manifest);
  }

  @Bean
  public AWSXRayRecorder buildGlobalRecorder(SamplingStrategy samplingStrategy,
                                             AwsXrayConfigurationProperties properties) {
    AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();

    switch (properties.samplingStrategy) {
      case ALL:
        builder.withSamplingStrategy(new AllSamplingStrategy());
        break;
      case NONE:
        builder.withSamplingStrategy(new NoSamplingStrategy());
        break;
      case STANDARD:
        builder.withSamplingStrategy(samplingStrategy);
        break;
      default:
        throw new BeanCreationException("Unknown sampling strategy provided: " + properties.samplingStrategy);
    }

    if (properties.ec2Plugin) {
      builder.withPlugin(new EC2Plugin());
    }
    if (properties.ecsPlugin) {
      builder.withPlugin(new ECSPlugin());
    }

    // TODO rz - The recorder is not threadsafe, and by default will throw exceptions if the context isn't available.
    // Much prefer our tracing not breaking the world, but would also like to correctly propagate segment contexts.
    builder.withContextMissingStrategy(new LogErrorContextMissingStrategy());

    AWSXRayRecorder recorder = builder.build();
    AWSXRay.setGlobalRecorder(recorder);

    return recorder;
  }


}
