package com.netflix.spinnaker.clouddriver.tencent.cache;

import com.google.common.base.CaseFormat;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import groovy.util.logging.Slf4j;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Slf4j
@Component("TencentKeys")
public class Keys implements KeyParser {
  @Override
  public String getCloudProvider() {
    return TencentCloudProvider.ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public Boolean canParseType(final String type) {
    return Arrays.stream(Namespace.values()).anyMatch(a -> a.ns.equals(type));
  }

  @Override
  public Boolean canParseField(String field) {
    return false;
  }

  public static Map<String, String> parse(String key) {
    // todo
    String[] parts = key.split(":");

    if (parts.length < 2 || !parts[0].equals(TencentCloudProvider.ID)) {
      return null;
    }

    HashMap<String, String> result =
        new HashMap<String, String>() {
          {
            put("provider", parts[0]);
            put("type", parts[1]);
          }
        };

    if (Namespace.CLUSTERS.ns.equals(result.get("type"))) {
      Names names = Names.parseName(parts[4]);
      result.put("application", parts[3]);
      result.put("account", parts[2]);
      result.put("name", parts[4]);
      result.put("cluster", parts[4]);
      result.put("stack", names.getStack());
      result.put("detail", names.getDetail());
    } else if (Keys.Namespace.IMAGES.ns.equals(result.get("type"))) {
      result.put("account", parts[2]);
      result.put("region", parts[3]);
      result.put("imageId", parts[4]);
    } else if (Namespace.NAMED_IMAGES.ns.equals(result.get("type"))) {
      result.put("account", parts[2]);
      result.put("imageName", parts[3]);
    } else if (Namespace.SECURITY_GROUPS.ns.equals(result.get("type"))) {
      Names names = Names.parseName(parts[2]);
      result.put("application", names.getApp());
      result.put("name", parts[2]);
      result.put("account", parts[3]);
      result.put("region", parts[4]);
      result.put("id", parts[5]);
    } else if (Namespace.NETWORKS.ns.equals(result.get("type"))) {
      result.put("account", parts[2]);
      result.put("region", parts[3]);
      result.put("id", parts[4]);
    } else if (Namespace.SUBNETS.ns.equals(result.get("type"))) {
      result.put("account", parts[2]);
      result.put("region", parts[3]);
      result.put("id", parts[4]);
    } else if (Namespace.LOAD_BALANCERS.ns.equals(result.get("type"))) {
      result.put("account", parts[2]);
      result.put("region", parts[3]);
      result.put("id", parts[4]);
    } else if (Namespace.SERVER_GROUPS.ns.equals(result.get("type"))) {
      result.put("account", parts[2]);
      result.put("region", parts[3]);
      result.put("cluster", parts[4]);
      result.put("name", parts[5]);
    } else {
      return null;
    }

    return result;
  }

  public static String getApplicationKey(final String application) {
    return TencentCloudProvider.ID + ":" + Namespace.APPLICATIONS + ":" + application.toLowerCase();
  }

  public static String getClusterKey(String clusterName, final String application, String account) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.CLUSTERS
        + ":"
        + account
        + ":"
        + application.toLowerCase()
        + ":"
        + clusterName;
  }

  public static String getServerGroupKey(String serverGroupName, String account, String region) {
    Names names = Names.parseName(serverGroupName);
    return getServerGroupKey(names.getCluster(), names.getGroup(), account, region);
  }

  public static String getServerGroupKey(
      String cluster, String serverGroupName, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.SERVER_GROUPS
        + ":"
        + account
        + ":"
        + region
        + ":"
        + cluster
        + ":"
        + serverGroupName;
  }

  public static String getInstanceKey(String instanceId, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.INSTANCES
        + ":"
        + account
        + ":"
        + region
        + ":"
        + instanceId;
  }

  public static String getImageKey(String imageId, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.IMAGES
        + ":"
        + account
        + ":"
        + region
        + ":"
        + imageId;
  }

  public static String getNamedImageKey(String imageName, String account) {
    return TencentCloudProvider.ID + ":" + Namespace.NAMED_IMAGES + ":" + account + ":" + imageName;
  }

  public static String getKeyPairKey(String keyId, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.KEY_PAIRS
        + ":"
        + account
        + ":"
        + region
        + ":"
        + keyId;
  }

  public static String getInstanceTypeKey(String account, String region, String instanceType) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.INSTANCE_TYPES
        + ":"
        + account
        + ":"
        + region
        + ":"
        + instanceType;
  }

  public static String getLoadBalancerKey(String loadBalancerId, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.LOAD_BALANCERS
        + ":"
        + account
        + ":"
        + region
        + ":"
        + loadBalancerId;
  }

  public static String getSecurityGroupKey(
      String securityGroupId, String securityGroupName, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.SECURITY_GROUPS
        + ":"
        + securityGroupName
        + ":"
        + account
        + ":"
        + region
        + ":"
        + securityGroupId;
  }

  public static String getNetworkKey(String networkId, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.NETWORKS
        + ":"
        + account
        + ":"
        + region
        + ":"
        + networkId;
  }

  public static String getSubnetKey(String subnetId, String account, String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.SUBNETS
        + ":"
        + account
        + ":"
        + region
        + ":"
        + subnetId;
  }

  public static String getTargetHealthKey(
      String loadBalancerId,
      String listenerId,
      String locationId,
      String instanceId,
      String account,
      String region) {
    return TencentCloudProvider.ID
        + ":"
        + Namespace.HEALTH_CHECKS
        + ":"
        + account
        + ":"
        + region
        + ":"
        + loadBalancerId
        + ":"
        + listenerId
        + ":"
        + locationId
        + ":"
        + instanceId;
  }

  public static enum Namespace {
    APPLICATIONS,
    CLUSTERS,
    HEALTH_CHECKS,
    LAUNCH_CONFIGS,
    IMAGES,
    NAMED_IMAGES,
    INSTANCES,
    INSTANCE_TYPES,
    KEY_PAIRS,
    LOAD_BALANCERS,
    NETWORKS,
    SECURITY_GROUPS,
    SERVER_GROUPS,
    SUBNETS,
    ON_DEMAND;

    private Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
    }

    public String toString() {
      return ns;
    }

    public final String ns;
  }
}
