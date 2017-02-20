package com.netflix.spinnaker.clouddriver.dcos.cache

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils

import static com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils.GLOBAL_REGION
import static com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils.isGlobalLoadBalancer

class Keys {
  public static final PROVIDER = "dcos"

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    INSTANCES,
    LOAD_BALANCERS,
    CLUSTERS,
    APPLICATIONS,
    HEALTH,
    ON_DEMAND

    final String ns

    private Namespace() {
      def parts = name().split('_')
      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    if (parts[0] != PROVIDER) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    switch (result.type) {
      case Namespace.IMAGES.ns:
        //TODO result << [account: parts[2], region: parts[3], imageId: parts[4]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[4])
        result << [
                account    : parts[2],
                name       : parts[4],
                region     : parts[3],
                serverGroup: parts[4],
                application: names.app,
                stack      : names.stack,
                cluster    : names.cluster,
                detail     : names.detail,
                sequence   : names.sequence?.toString()
        ]
        break
      case Namespace.INSTANCES.ns:
        //TODO result << [id: parts[2]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        result << [
                account    : parts[2],
                application: parts[3],
                name       : parts[4],
                cluster    : parts[4],
                stack      : names.stack,
                detail     : names.detail]
        //TODO result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        result << [
                application: parts[2]
        ]
        break
      case Namespace.HEALTH.ns:
        //TODO result << [id: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[3])
        result << [
                account     : parts[2],
                name        : parts[3],
                loadBalancer: parts[3],
                application : names.app,
                stack       : names.stack,
                detail      : names.detail
        ]
        break
      default:
        return null
        break
    }

    result
  }

  static String getApplicationKey(String application) {
    "${PROVIDER}:${Namespace.APPLICATIONS}:${application}"
  }

  static String getServerGroupKey(DcosSpinnakerId id) {
    "${PROVIDER}:${Namespace.SERVER_GROUPS}:${id.account}:${id.safeRegion}:${id.name}"
  }

  static String getClusterKey(String account, String application, String cluster) {
    "${PROVIDER}:${Namespace.CLUSTERS}:${account}:${application}:${cluster}"
  }

  static String getInstanceKey(DcosSpinnakerId appId, String taskName) {
    "${PROVIDER}:${Namespace.INSTANCES}:${appId.account}:${appId.safeRegion}:${taskName}"
  }

  static String getInstanceKey(String account, String safeRegion, String taskName) {
    "${PROVIDER}:${Namespace.INSTANCES}:${account}:${safeRegion}:${taskName}"
  }

  static String getLoadBalancerKey(String account, String region, String loadBalancerName) {
    "${PROVIDER}:${Namespace.LOAD_BALANCERS}:${account}:${region}:${loadBalancerName}"
  }

  static String getLoadBalancerKey(DcosSpinnakerId appId) {
    isGlobalLoadBalancer(appId) ? "${PROVIDER}:${Namespace.LOAD_BALANCERS}:${appId.account}:${GLOBAL_REGION}:${appId.name}"
            : "${PROVIDER}:${Namespace.LOAD_BALANCERS}:${appId.account}:${appId.safeRegion}:${appId.name}"
  }
}
