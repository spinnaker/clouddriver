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

import com.amazonaws.xray.strategy.sampling.SamplingRuleManifest;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aws.xray")
public class AwsXrayConfigurationProperties {

  SamplingStrategy samplingStrategy = SamplingStrategy.STANDARD;

  boolean ec2Plugin = true;
  boolean ecsPlugin = false;

  SamplingRuleManifest manifest;

  public boolean isEc2Plugin() {
    return ec2Plugin;
  }

  public SamplingStrategy getSamplingStrategy() {
    return samplingStrategy;
  }

  public void setSamplingStrategy(SamplingStrategy samplingStrategy) {
    this.samplingStrategy = samplingStrategy;
  }

  public void setEc2Plugin(boolean ec2Plugin) {
    this.ec2Plugin = ec2Plugin;
  }

  public boolean isEcsPlugin() {
    return ecsPlugin;
  }

  public void setEcsPlugin(boolean ecsPlugin) {
    this.ecsPlugin = ecsPlugin;
  }

  public SamplingRuleManifest getManifest() {
    return manifest;
  }

  public void setManifest(SamplingRuleManifest manifest) {
    this.manifest = manifest;
  }

  public enum SamplingStrategy {
    NONE,
    ALL,
    STANDARD
  }
}
