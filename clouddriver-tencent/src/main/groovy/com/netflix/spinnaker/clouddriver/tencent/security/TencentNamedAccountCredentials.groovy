package com.netflix.spinnaker.clouddriver.tencent.security

import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource
import com.netflix.spinnaker.clouddriver.tencent.names.TencentBasicResourceNamer
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.moniker.Namer
import groovy.transform.Canonical
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j

@Slf4j
@Canonical
@TupleConstructor
class TencentNamedAccountCredentials implements AccountCredentials<TencentCredentials> {
  final String cloudProvider = TencentCloudProvider.ID

  final String name
  final String environment
  final String accountType
  final TencentCredentials credentials
  List<TencentRegion> regions

  final List<String> requiredGroupMembership
  final Permissions permissions

  Namer namer = new TencentBasicResourceNamer()

  TencentNamedAccountCredentials(
    String name,
    String environment,
    String accountType,
    String secretId,
    String secretKey,
    List<String> regions,
    String clouddriverUserAgentApplicationName
  ){
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.credentials = new TencentCredentials(secretId, secretKey)
    this.regions = buildRegions(regions)
    NamerRegistry.lookup()
      .withProvider(TencentCloudProvider.ID)
      .withAccount(name)
      .setNamer(TencentBasicResource.class, namer)
  }

  private static List<TencentRegion> buildRegions(List<String> regions) {
    regions?.collect {new TencentRegion(it)} ?: new ArrayList<TencentRegion>()
  }

  static class TencentRegion {
    public final String name

    TencentRegion(String name) {
      if (name == null) {
        throw new IllegalArgumentException("name must be specified.")
      }
      this.name = name
    }

    String getName() {return name}

    @Override
    boolean equals(Object o) {
      if (this == o) return true
      if (o == null || getClass() != o.getClass()) return false

      TencentRegion region = (TencentRegion) o

      name.equals(region.name)
    }

    @Override
    int hashCode() {
      name.hashCode()
    }
  }
}
