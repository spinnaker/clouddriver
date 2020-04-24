/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.cache;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider.ID;
import static java.util.Collections.emptyMap;

import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component("CloudFoundryInfraKeys")
public class Keys implements KeyParser {
  public static Optional<Map<String, String>> parse(String key) {
    String[] parts = key.split(":");

    if (parts.length < 2 || !parts[0].equals(ID)) {
      return Optional.empty();
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);

    String type = parts[1];
    result.put("type", type);

    if (Namespace.APPLICATIONS.ns.equals(type)) {
      result.put("name", parts[2]);
    } else if (Namespace.LOAD_BALANCERS.ns.equals(type)) {
      result.put("account", parts[2]);
      result.put("id", parts[3]);
      result.put("host", parts[4].isEmpty() ? null : parts[4]);
      result.put("domain", parts[5]);
      result.put("path", parts[6].isEmpty() ? null : parts[6]);
      result.put("port", Integer.parseInt(parts[7]) == -1 ? null : parts[7]);
      result.put("region", parts[8]);
    } else if (Namespace.CLUSTERS.ns.equals(type)) {
      result.put("account", parts[2]);
      result.put("guid", parts[3]);
      result.put("name", parts[4]);
    } else if (Namespace.INSTANCES.ns.equals(type)) {
      result.put("account", parts[2]);
      result.put("appGuid", parts[3]);
      result.put("name", parts[4]);
    } else if (Namespace.SERVER_GROUPS.ns.equals(type)) {
      result.put("account", parts[2]);
      result.put("serverGroup", parts[3]);
      result.put("region", parts[4]);
    } else {
      return Optional.empty();
    }

    return Optional.of(result);
  }

  public static String getApplicationKey(String app) {
    return ID + ":" + Namespace.APPLICATIONS + ":" + app.toLowerCase();
  }

  public static String getSpaceKey(String account, String region) {
    return ID + ":" + Namespace.SPACES + ":" + account + ":" + region;
  }

  public static String getAllSpacesKey(String account) {
    return ID + ":" + Namespace.SPACES + ":" + account + ":*";
  }

  public static String getAllLoadBalancers() {
    return ID + ":" + Namespace.LOAD_BALANCERS + ":*";
  }

  public static String getLoadBalancerKey(String account, CloudFoundryLoadBalancer lb) {
    return ID
        + ":"
        + Namespace.LOAD_BALANCERS
        + ":"
        + account
        + ":"
        + lb.getId()
        + ":"
        + (lb.getHost() != null ? lb.getHost() : "")
        + ":"
        + lb.getDomain().getName()
        + ":"
        + (lb.getPath() != null ? lb.getPath() : "")
        + ":"
        + (lb.getPort() != null ? lb.getPort() : -1)
        + ":"
        + lb.getRegion();
  }

  public static String getLoadBalancerKey(String account, String uri, String region) {
    Pattern VALID_ROUTE_REGEX =
        Pattern.compile("^([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_.-]+)(:[0-9]+)?([/a-zA-Z0-9_-]+)?$");
    Matcher matcher = VALID_ROUTE_REGEX.matcher(uri);
    if (matcher.find()) {
      String host = Optional.ofNullable(matcher.group(1)).orElse("*");
      String domain = Optional.ofNullable(matcher.group(2)).orElse("*");
      String port = Optional.ofNullable(matcher.group(3)).orElse("-1");
      String path = Optional.ofNullable(matcher.group(4)).orElse("");
      return ID
          + ":"
          + Namespace.LOAD_BALANCERS
          + ":"
          + account
          + ":*:"
          + host
          + ":"
          + domain
          + ":"
          + path
          + ":"
          + port
          + ":"
          + region;
    } else {
      return null;
    }
  }

  public static String getClusterKey(String account, String app, String name) {
    return ID
        + ":"
        + Namespace.CLUSTERS
        + ":"
        + account
        + ":"
        + app.toLowerCase()
        + ":"
        + name.toLowerCase();
  }

  public static String getServerGroupKey(String account, String name, String region) {
    return ID
        + ":"
        + Namespace.SERVER_GROUPS
        + ":"
        + account
        + ":"
        + name.toLowerCase()
        + ":"
        + region;
  }

  public static String getInstanceKey(String account, String instanceName) {
    return ID + ":" + Namespace.INSTANCES + ":" + account + ":" + instanceName;
  }

  @Override
  public String getCloudProvider() {
    // This is intentionally 'aws'. Refer to todos in SearchController#search for why.
    return "aws";
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key).orElse(emptyMap());
  }

  @Override
  public Boolean canParseType(String type) {
    return Arrays.stream(Namespace.values()).anyMatch(it -> it.ns.equals(type));
  }

  @Override
  public Boolean canParseField(String field) {
    return false;
  }

  @Getter
  public enum Namespace {
    APPLICATIONS("applications"),
    CLUSTERS("clusters"),
    INSTANCES("instances"),
    LOAD_BALANCERS("loadBalancers"),
    ON_DEMAND("onDemand"),
    SERVER_GROUPS("serverGroups"),
    SPACES("spaces");

    final String ns;

    Namespace(String ns) {
      this.ns = ns;
    }

    public String toString() {
      return ns;
    }
  }
}
