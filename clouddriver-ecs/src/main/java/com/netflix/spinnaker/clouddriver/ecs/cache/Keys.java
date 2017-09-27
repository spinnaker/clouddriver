package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;

import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID;

public class Keys implements KeyParser {
  public enum Namespace {
    SERVICES;

    final String ns;

    private Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    public String toString() {
      return ns;
    }
  }

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    String[] parts = key.split(":");

    if (parts.length < 3 || !parts[0].equals(ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);

    switch (Namespace.valueOf(parts[1])) {
      case SERVICES:
        result.put("account", parts[2]);
        result.put("region", parts[3]);
        result.put("serviceName", parts[4]);
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
    return ID + ":" + Namespace.SERVICES + ":" + account + ":" + region + ":" + serviceName;
  }
}
