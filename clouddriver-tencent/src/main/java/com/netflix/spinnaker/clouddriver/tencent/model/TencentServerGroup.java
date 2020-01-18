package com.netflix.spinnaker.clouddriver.tencent.model;

import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TencentServerGroup implements ServerGroup, TencentBasicResource {
  private final String type = TencentCloudProvider.ID;
  private final String cloudProvider = TencentCloudProvider.ID;
  private String accountName;
  private String name;
  private String region;
  private Set<String> zones;
  @Builder.Default private Set<TencentInstance> instances = new HashSet<>();
  @Builder.Default private Map<String, Object> image = new HashMap<>();
  @Builder.Default private Map<String, Object> launchConfig = new HashMap<>();
  @Builder.Default private Map<String, Object> asg = new HashMap<>();
  private Map buildInfo;
  private String vpcId;

  private List<Map> scalingPolicies;
  private List<Map> scheduledActions;

  @Builder.Default private Boolean disabled = false;

  @Override
  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(accountName)
        .withResource(TencentBasicResource.class)
        .deriveMoniker(this);
  }

  @Override
  public String getMonikerName() {
    return name;
  }

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  @Override
  public Long getCreatedTime() {
    Date dateTime = AutoScalingClient.ConvertIsoDateTime((String) asg.get("createdTime"));
    return dateTime != null ? dateTime.getTime() : null;
  }

  @Override
  public Set<String> getLoadBalancers() {
    Set<String> loadBalancerNames = new HashSet<>();
    if (asg != null && asg.containsKey("forwardLoadBalancerSet")) {
      List<Map<String, Object>> forwardLoadBalancerList =
          (List<Map<String, Object>>) asg.get("forwardLoadBalancerSet");
      loadBalancerNames.addAll(
          forwardLoadBalancerList.stream()
              .map(it -> (String) it.get("loadBalancerId"))
              .collect(Collectors.toList()));
    }

    if (asg != null && asg.containsKey("loadBalancerIdSet")) {
      loadBalancerNames.addAll((List<String>) asg.get("loadBalancerIdSet"));
    }

    return loadBalancerNames;
  }

  @Override
  public Set<String> getSecurityGroups() {
    Set<String> securityGroups = new HashSet<>();
    if (launchConfig != null && launchConfig.containsKey("securityGroupIds")) {
      securityGroups = (Set<String>) launchConfig.get("securityGroupIds");
    }
    return securityGroups;
  }

  @Override
  public InstanceCounts getInstanceCounts() {
    Collection<Instance> upInstances = filterInstancesByHealthState(instances, HealthState.Up);
    Collection<Instance> downInstances = filterInstancesByHealthState(instances, HealthState.Down);
    Collection<Instance> unknownInstances =
        filterInstancesByHealthState(instances, HealthState.Unknown);
    Collection<Instance> outOfServiceInstances =
        filterInstancesByHealthState(instances, HealthState.OutOfService);
    Collection<Instance> startingInstances =
        filterInstancesByHealthState(instances, HealthState.Starting);
    return new InstanceCounts(
        CollectionUtils.isEmpty(instances) ? 0 : instances.size(),
        upInstances.size(),
        downInstances.size(),
        unknownInstances.size(),
        outOfServiceInstances.size(),
        startingInstances.size());
  }

  @Override
  public Capacity getCapacity() {
    if (asg != null) {
      return new Capacity(
          (int) asg.getOrDefault("minSize", 0),
          (int) asg.getOrDefault("maxSize", 0),
          (int) asg.getOrDefault("desiredCapacity", 0));
    } else {
      return null;
    }
  }

  @Override
  public ImagesSummary getImagesSummary() {
    Map buildInfo = this.buildInfo;
    Map<String, Object> image = this.image;
    return new TencentImagesSummary(
        new ArrayList<TencentImageSummary>() {
          {
            add(new TencentImageSummary(name, image, buildInfo));
          }
        });
  }

  @Override
  public ImageSummary getImageSummary() {
    return getImagesSummary().getSummaries().get(0);
  }

  static Collection<Instance> filterInstancesByHealthState(
      Collection<? extends Instance> instances, HealthState healthState) {
    return instances.stream()
        .filter(it -> it.getHealthState() == healthState)
        .collect(Collectors.toList());
  }

  @AllArgsConstructor
  static class TencentImagesSummary implements ImagesSummary {
    List<TencentImageSummary> summaries;

    @Override
    public List<? extends ImageSummary> getSummaries() {
      return summaries;
    }
  }

  static class TencentImageSummary implements ImageSummary {
    public TencentImageSummary(
        String name, Map<String, Object> image, Map<String, Object> buildInfo) {
      serverGroupName = name;
      imageName = (String) image.getOrDefault("name", null);
      imageId = (String) image.getOrDefault("imageId", null);
      this.buildInfo = buildInfo;
      this.image = image;
    }

    private String serverGroupName;
    private String imageName;
    private String imageId;
    private final Map<String, Object> buildInfo;
    private final Map<String, Object> image;

    @Override
    public String getServerGroupName() {
      return serverGroupName;
    }

    @Override
    public String getImageId() {
      return imageId;
    }

    @Override
    public String getImageName() {
      return imageName;
    }

    @Override
    public Map<String, Object> getImage() {
      return this.image;
    }

    @Override
    public Map<String, Object> getBuildInfo() {
      return this.buildInfo;
    }
  }
}
