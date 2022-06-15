/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.security;

import com.aliyuncs.auth.AlibabaCloudCredentials;
import com.aliyuncs.auth.AlibabaCloudCredentialsProvider;
import com.aliyuncs.exceptions.ClientException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import java.util.List;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
public class AliCloudCredentials extends AbstractAccountCredentials<AlibabaCloudCredentials> {

  private static final String CLOUD_PROVIDER = "alicloud";

  private final String name;
  private final String environment;
  private final String accountType;

  private final List<String> regions;

  private final List<String> requiredGroupMembership;

  private final AlibabaCloudCredentialsProvider credentialsProvider;

  public AliCloudCredentials(
      String name,
      String environment,
      String accountType,
      List<String> regions,
      List<String> requiredGroupMembership,
      AlibabaCloudCredentialsProvider credentialsProvider) {
    this.name = name;
    this.environment = environment;
    this.accountType = accountType;
    this.regions = regions;
    this.requiredGroupMembership = requiredGroupMembership;
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getEnvironment() {
    return environment;
  }

  @Override
  public String getAccountType() {
    return accountType;
  }

  @Override
  @JsonIgnore
  public AlibabaCloudCredentials getCredentials() {
    try {
      return credentialsProvider.getCredentials();
    } catch (ClientException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  public List<String> getRegions() {
    return regions;
  }

  @JsonIgnore
  public AlibabaCloudCredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }
}
