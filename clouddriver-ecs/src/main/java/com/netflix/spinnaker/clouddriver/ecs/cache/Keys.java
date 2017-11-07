/*
 * * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;

import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID;

public class Keys implements KeyParser {
  public enum Namespace {
    IAM_ROLE,
    SERVICES,
    ECS_CLUSTERS,
    TASKS,
    CONTAINER_INSTANCES,
    TASK_DEFINITIONS;

    final String ns;

    Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    public String toString() {
      return ns;
    }
  }

  public static final String SEPARATOR = ":";

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(SEPARATOR);

    if (parts.length < 3 || !parts[0].equals(ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);
    result.put("account", parts[2]);


    Namespace namespace = Namespace.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parts[1]));

    if (!namespace.equals(Namespace.IAM_ROLE)) {
      result.put("region", parts[3]);
    }

    switch (namespace) {
      case SERVICES:
        result.put("serviceName", parts[4]);
        break;
      case ECS_CLUSTERS:
        result.put("clusterName", parts[4]);
        break;
      case TASKS:
        result.put("taskName", parts[4]);
        break;
      case CONTAINER_INSTANCES:
        result.put("containerInstanceArn", parts[4]);
        break;
      case TASK_DEFINITIONS:
        result.put("taskDefinitionArn", parts[4]);
        break;
      case IAM_ROLE:
        result.put("roleName", parts[3]);
      default:
        break;
    }

    return result;
  }

  public static String getServiceKey(String account, String region, String serviceName) {
    return ID + SEPARATOR + Namespace.SERVICES + SEPARATOR + account + SEPARATOR + region + SEPARATOR + serviceName;
  }

  public static String getClusterKey(String account, String region, String clusterName) {
    return ID + SEPARATOR + Namespace.ECS_CLUSTERS + SEPARATOR + account + SEPARATOR + region + SEPARATOR + clusterName;
  }

  public static String getTaskKey(String account, String region, String taskId) {
    return ID + SEPARATOR + Namespace.TASKS + SEPARATOR + account + SEPARATOR + region + SEPARATOR + taskId;
  }

  public static String getTaskHealthKey(String account, String region, String taskId) {
    return ID + SEPARATOR + HEALTH + SEPARATOR + account + SEPARATOR + region + SEPARATOR + taskId;
  }

  public static String getContainerInstanceKey(String account, String region, String containerInstanceArn) {
    return ID + SEPARATOR + Namespace.CONTAINER_INSTANCES + SEPARATOR + account + SEPARATOR + region + SEPARATOR + containerInstanceArn;
  }

  public static String getTaskDefinitionKey(String account, String region, String taskDefinitionArn) {
    return ID + SEPARATOR + Namespace.TASK_DEFINITIONS + SEPARATOR + account + SEPARATOR + region + SEPARATOR + taskDefinitionArn;
  }

  public static String getIamRoleKey(String account, String iamRoleName) {
    return ID + SEPARATOR + Namespace.IAM_ROLE + SEPARATOR + account + SEPARATOR + iamRoleName;
  }

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public Boolean canParseType(String type) {
    for (Namespace key : Namespace.values()) {
      if (key.toString().equals(type)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Boolean canParseField(String type) {
    return false;
  }
}
