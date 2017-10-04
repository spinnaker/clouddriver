package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;

import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID;

public class Keys implements KeyParser {
  public enum Namespace {
    SERVICES,
    ECS_CLUSTERS,
    TASKS,
    CONTAINER_INSTANCES;

    final String ns;

    Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    public String toString() {
      return ns;
    }
  }

  private static final String SEPARATOR = ":";

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(SEPARATOR);

    if (parts.length < 3 || !parts[0].equals(ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);

    switch (Namespace.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parts[1]))) {
      case SERVICES:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("serviceName", parts[4]);
        break;
      case ECS_CLUSTERS:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("clusterName", parts[4]);
        break;
      case TASKS:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("taskName", parts[4]);
        break;
      case CONTAINER_INSTANCES:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("containerInstanceArn", parts[4]);
        break;
      default:
        break;
    }

    return result;
  }

  @Override
  public Boolean canParse(String type) {
    for (Namespace key : Namespace.values()) {
      if (key.toString().equals(type)) {
        return true;
      }
    }
    return false;
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

  public static String getTaskHealthKey(String account, String region, String taslId) {
    return ID + SEPARATOR + com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH + SEPARATOR + account + SEPARATOR + region + SEPARATOR + taslId;
  }

  public static String getContainerInstanceKey(String account, String region, String containerInstanceArn) {
    return ID + SEPARATOR + Namespace.CONTAINER_INSTANCES + SEPARATOR + account + SEPARATOR + region + SEPARATOR + containerInstanceArn;
  }
}
