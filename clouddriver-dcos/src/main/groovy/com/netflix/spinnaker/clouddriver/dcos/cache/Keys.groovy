package com.netflix.spinnaker.clouddriver.dcos.cache

import com.netflix.frigga.Names

class Keys {
  public static final PROVIDER = "dcos"
  public static final DEFAULT_REGION = "default"

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    INSTANCES,
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
        def names = Names.parseName(parts[5])
        //TODO result << [application: names.app.toLowerCase(), cluster: parts[2], account: parts[3], region: parts[4], serverGroup: parts[5], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
        break
      case Namespace.INSTANCES.ns:
        //TODO result << [id: parts[2]]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        //TODO result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        //TODO result << [application: parts[2].toLowerCase()]
        break
      case Namespace.HEALTH.ns:
        //TODO result << [id: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
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

  static String getServerGroupKey(String account, String dcosAppId) {
    // app ids may contain leading "/" which is a problem for the way that these keys
    // are built from path parameters for queries
    // TODO: better translation strategy
    "${PROVIDER}:${Namespace.SERVER_GROUPS}:${account}:${dcosAppId.replace("/", "_")}"
  }

  static String getClusterKey(String account, String application, String cluster) {
    "${PROVIDER}:${Namespace.CLUSTERS}:${account}:${application}:${cluster}"
  }

  /**
   *
   * @param account
   * @param dcosAppId - the app id, not including any groups
   * @param name - the full task name
   * @return
   */
  static String getInstanceKey(String account, String dcosAppId, String name) {
    // TODO: better translation strategy
    "${PROVIDER}:${Namespace.INSTANCES}:${account}:${dcosAppId.replace("/", "_")}:${name}"
  }
}
