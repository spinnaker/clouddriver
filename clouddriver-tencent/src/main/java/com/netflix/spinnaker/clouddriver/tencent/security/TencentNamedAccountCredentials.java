package com.netflix.spinnaker.clouddriver.tencent.security;

import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource;
import com.netflix.spinnaker.clouddriver.tencent.names.TencentBasicResourceNamer;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Data
@Slf4j
public class TencentNamedAccountCredentials implements AccountCredentials<TencentCredentials> {
  public TencentNamedAccountCredentials(
      String name,
      String environment,
      String accountType,
      String secretId,
      String secretKey,
      List<String> regions,
      String clouddriverUserAgentApplicationName) {
    this.name = name;
    this.environment = environment;
    this.accountType = accountType;
    this.credentials = new TencentCredentials(secretId, secretKey);
    this.regions = buildRegions(regions);
    NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(name)
        .setNamer(TencentBasicResource.class, namer);
  }

  private static List<TencentRegion> buildRegions(List<String> regions) {
    final List<TencentRegion> collect =
        regions.stream().map(it -> new TencentRegion(it)).collect(Collectors.toList());
    return !CollectionUtils.isEmpty(collect) ? collect : new ArrayList<>();
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final String getName() {
    return name;
  }

  public final String getEnvironment() {
    return environment;
  }

  public final String getAccountType() {
    return accountType;
  }

  public final TencentCredentials getCredentials() {
    return credentials;
  }

  public List<TencentRegion> getRegions() {
    return regions;
  }

  public void setRegions(List<TencentRegion> regions) {
    this.regions = regions;
  }

  public final List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  public final Permissions getPermissions() {
    return permissions;
  }

  public TencentBasicResourceNamer getNamer() {
    return namer;
  }

  public void setNamer(TencentBasicResourceNamer namer) {
    this.namer = namer;
  }

  private final String cloudProvider = TencentCloudProvider.ID;
  private final String name;
  private final String environment;
  private final String accountType;
  private final TencentCredentials credentials;
  private List<TencentRegion> regions;
  private List<String> requiredGroupMembership;
  private Permissions permissions;
  private TencentBasicResourceNamer namer = new TencentBasicResourceNamer();

  public static class TencentRegion {
    public TencentRegion(String name) {
      if (name == null) {
        throw new IllegalArgumentException("name must be specified.");
      }

      this.name = name;
    }

    public String getName() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this.equals(o)) return true;
      if (o == null || !getClass().equals(o.getClass())) return false;

      TencentRegion region = (TencentRegion) o;

      return name.equals(region.getName());
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    public final String name;
  }
}
