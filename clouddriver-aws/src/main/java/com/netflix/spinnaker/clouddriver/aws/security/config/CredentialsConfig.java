/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.config;

import static lombok.EqualsAndHashCode.Include;

import java.util.List;
import lombok.EqualsAndHashCode;

/**
 * A mutable credentials configurations structure suitable for transformation into concrete
 * credentials implementations.
 */
public class CredentialsConfig {
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public static class Region {
    @Include private String name;
    private List<String> availabilityZones;
    private List<String> preferredZones;
    private Boolean deprecated;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getAvailabilityZones() {
      return availabilityZones;
    }

    public void setAvailabilityZones(List<String> availabilityZones) {
      this.availabilityZones = availabilityZones;
    }

    public List<String> getPreferredZones() {
      return preferredZones;
    }

    public void setPreferredZones(List<String> preferredZones) {
      this.preferredZones = preferredZones;
    }

    public Boolean getDeprecated() {
      return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
      this.deprecated = deprecated;
    }

    public Region copyOf() {
      Region clone = new Region();
      clone.setName(getName());
      clone.setAvailabilityZones(getAvailabilityZones());
      clone.setPreferredZones(getPreferredZones());
      clone.setDeprecated(getDeprecated());

      return clone;
    }
  }

  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public static class LifecycleHook {
    @Include private String name;
    @Include private String roleARN;
    @Include private String notificationTargetARN;
    @Include private String lifecycleTransition;
    @Include private Integer heartbeatTimeout;
    @Include private String defaultResult;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getRoleARN() {
      return roleARN;
    }

    public void setRoleARN(String roleARN) {
      this.roleARN = roleARN;
    }

    public String getNotificationTargetARN() {
      return notificationTargetARN;
    }

    public void setNotificationTargetARN(String notificationTargetARN) {
      this.notificationTargetARN = notificationTargetARN;
    }

    public String getLifecycleTransition() {
      return lifecycleTransition;
    }

    public void setLifecycleTransition(String lifecycleTransition) {
      this.lifecycleTransition = lifecycleTransition;
    }

    public Integer getHeartbeatTimeout() {
      return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(Integer heartbeatTimeout) {
      this.heartbeatTimeout = heartbeatTimeout;
    }

    public String getDefaultResult() {
      return defaultResult;
    }

    public void setDefaultResult(String defaultResult) {
      this.defaultResult = defaultResult;
    }
  }

  private String accessKeyId;
  private String secretAccessKey;
  private String defaultKeyPairTemplate;
  private List<Region> defaultRegions;
  private List<String> defaultSecurityGroups;
  private List<LifecycleHook> defaultLifecycleHooks;
  private String defaultEddaTemplate;
  private String defaultFront50Template;
  private String defaultBastionHostTemplate;
  private String defaultDiscoveryTemplate;
  private String defaultAssumeRole;
  private String defaultSessionName;
  private String defaultLifecycleHookRoleARNTemplate;
  private String defaultLifecycleHookNotificationTargetARNTemplate;

  public String getDefaultKeyPairTemplate() {
    return defaultKeyPairTemplate;
  }

  public void setDefaultKeyPairTemplate(String defaultKeyPairTemplate) {
    this.defaultKeyPairTemplate = defaultKeyPairTemplate;
  }

  public List<Region> getDefaultRegions() {
    return defaultRegions;
  }

  public void setDefaultRegions(List<Region> defaultRegions) {
    this.defaultRegions = defaultRegions;
  }

  public List<String> getDefaultSecurityGroups() {
    return defaultSecurityGroups;
  }

  public void setDefaultSecurityGroups(List<String> defaultSecurityGroups) {
    this.defaultSecurityGroups = defaultSecurityGroups;
  }

  public String getDefaultEddaTemplate() {
    return defaultEddaTemplate;
  }

  public void setDefaultEddaTemplate(String defaultEddaTemplate) {
    this.defaultEddaTemplate = defaultEddaTemplate;
  }

  public String getDefaultFront50Template() {
    return defaultFront50Template;
  }

  public void setDefaultFront50Template(String defaultFront50Template) {
    this.defaultFront50Template = defaultFront50Template;
  }

  public String getDefaultBastionHostTemplate() {
    return defaultBastionHostTemplate;
  }

  public void setDefaultBastionHostTemplate(String defaultBastionHostTemplate) {
    this.defaultBastionHostTemplate = defaultBastionHostTemplate;
  }

  public String getDefaultDiscoveryTemplate() {
    return defaultDiscoveryTemplate;
  }

  public void setDefaultDiscoveryTemplate(String defaultDiscoveryTemplate) {
    this.defaultDiscoveryTemplate = defaultDiscoveryTemplate;
  }

  public String getDefaultAssumeRole() {
    return defaultAssumeRole;
  }

  public void setDefaultAssumeRole(String defaultAssumeRole) {
    this.defaultAssumeRole = defaultAssumeRole;
  }

  public String getDefaultSessionName() {
    return defaultSessionName;
  }

  public void setDefaultSessionName(String defaultSessionName) {
    this.defaultSessionName = defaultSessionName;
  }

  public List<LifecycleHook> getDefaultLifecycleHooks() {
    return defaultLifecycleHooks;
  }

  public void setDefaultLifecycleHooks(List<LifecycleHook> defaultLifecycleHooks) {
    this.defaultLifecycleHooks = defaultLifecycleHooks;
  }

  public String getDefaultLifecycleHookRoleARNTemplate() {
    return defaultLifecycleHookRoleARNTemplate;
  }

  public void setDefaultLifecycleHookRoleARNTemplate(String defaultLifecycleHookRoleARNTemplate) {
    this.defaultLifecycleHookRoleARNTemplate = defaultLifecycleHookRoleARNTemplate;
  }

  public String getDefaultLifecycleHookNotificationTargetARNTemplate() {
    return defaultLifecycleHookNotificationTargetARNTemplate;
  }

  public void setDefaultLifecycleHookNotificationTargetARNTemplate(
      String defaultLifecycleHookNotificationTargetARNTemplate) {
    this.defaultLifecycleHookNotificationTargetARNTemplate =
        defaultLifecycleHookNotificationTargetARNTemplate;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public void setSecretAccessKey(String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
  }
}
