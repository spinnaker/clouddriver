/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.yandex.provider;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import groovy.util.logging.Slf4j;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Slf4j
@Component("YandexKeys")
public class Keys implements KeyParser {
  @Override
  public String getCloudProvider() {
    // This is intentionally 'aws'. Refer to todos in SearchController#search for why.
    return "aws";
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public Boolean canParseType(final String type) {
    return Stream.of(Namespace.values()).anyMatch(it -> it.getNs().equals(type));
  }

  @Override
  public Boolean canParseField(String field) {
    return false;
  }

  @Nullable
  public static Map<String, String> parse(String key) {
    String[] parts = key.split(":");

    if (parts.length < 2 || !parts[0].equals(YandexCloudProvider.ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>(2);
    result.put("provider", parts[0]);
    result.put("type", parts[1]);

    Namespace namespace = Namespace.from(parts[1]);
    switch (namespace) {
      case CLUSTERS:
        {
          result.put("account", parts[2]);
          result.put("application", parts[3]);
          result.put("name", parts[4]);
          Names names = Names.parseName(parts[4]);
          result.put("cluster", names.getCluster());
          result.put("stack", names.getStack());
          result.put("detail", names.getDetail());
          break;
        }
      case APPLICATIONS:
        result.put("name", parts[2]);
        break;
      case INSTANCES:
      case LOAD_BALANCERS:
      case NETWORKS:
      case SERVER_GROUPS:
      case SUBNETS:
      case IMAGES:
        result.put("id", parts[2]);
        result.put("account", parts[3]);
        result.put("region", parts[4]);
        result.put("folder", parts[5]);
        result.put("name", parts.length < 7 ? "" : parts[6]);
        break;
      case ON_DEMAND:
        break;
    }

    return result;
  }

  public static String getApplicationKey(String name) {
    return YandexCloudProvider.ID + ":" + Namespace.APPLICATIONS + ":" + name;
  }

  public static String getClusterKey(String account, String application, String clusterName) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.CLUSTERS
        + ":"
        + account
        + ":"
        + application
        + ":"
        + clusterName;
  }

  public static String getNetworkKey(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.NETWORKS
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public static String getSubnetKey(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.SUBNETS
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public static String getLoadBalancerKey(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.LOAD_BALANCERS
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public static String getInstanceKey(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.INSTANCES
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public static String getServerGroupKey(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.SERVER_GROUPS
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public static String getImageKey(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.IMAGES
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public static String getServiceAccount(String account, String id, String folderId, String name) {
    return YandexCloudProvider.ID
        + ":"
        + Namespace.SERVICE_ACCOUNT
        + ":"
        + id
        + ":"
        + account
        + ":ru-central1:"
        + folderId
        + ":"
        + name;
  }

  public enum Namespace {
    APPLICATIONS,
    CLUSTERS,
    INSTANCES,
    LOAD_BALANCERS,
    NETWORKS,
    SERVER_GROUPS,
    SUBNETS,
    IMAGES,
    SERVICE_ACCOUNT,
    ON_DEMAND;

    @Getter private final String ns;

    Namespace() {
      this.ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name()); // FOO_BAR -> fooBar
    }

    @Override
    public String toString() {
      return ns;
    }

    public static Namespace from(String ns) {
      String cleanNs = Strings.nullToEmpty(ns);
      return Stream.of(values())
          .filter(namespace -> namespace.ns.equals(cleanNs))
          .findAny()
          .orElseThrow(IllegalArgumentException::new);
    }
  }
}
