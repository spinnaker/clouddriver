package com.netflix.spinnaker.clouddriver.tencent.deploy.description;

import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable;
import java.io.Serializable;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class TencentDeployDescription extends AbstractTencentCredentialsDescription
    implements DeployDescription, Serializable {
  private String application;
  private String stack;
  private String freeFormDetails;
  private String region;
  private String accountName;
  private String serverGroupName;
  private String instanceType;
  private String imageId;
  private Integer projectId;
  private Map<String, Object> systemDisk;
  private List<Map<String, Object>> dataDisks = new ArrayList<>();
  private Map<String, Object> internetAccessible;
  private Map<String, Object> loginSettings;
  private List<String> securityGroupIds;
  private Map<String, Object> enhancedService;
  private String userData;
  private String instanceChargeType;
  private Map<String, Object> instanceMarketOptionsRequest;
  private List<String> instanceTypes;
  private String instanceTypesCheckPolicy;
  private List<Map<String, String>> instanceTags;
  private Integer maxSize;
  private Integer minSize;
  private Integer desiredCapacity;
  private String vpcId;
  private Integer defaultCooldown;
  private List<String> loadBalancerIds = new ArrayList<>();
  private List<Map<String, Object>> forwardLoadBalancers = new ArrayList<>();
  private List<String> subnetIds = new ArrayList<>();
  private List<String> terminationPolicies = new ArrayList<>();
  private List<String> zones = new ArrayList<>();
  private String retryPolicy;
  private String zonesCheckPolicy;
  private Source source = new Source();
  private boolean copySourceScalingPoliciesAndActions = true;

  @Data
  public static class Source implements ServerGroupsNameable {
    @Override
    public Collection<String> getServerGroupNames() {
      return Collections.singletonList(serverGroupName);
    }

    private String region;
    private String serverGroupName;
    private Boolean useSourceCapacity;
  }
}
