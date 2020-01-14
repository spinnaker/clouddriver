package com.netflix.spinnaker.clouddriver.tencent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.moniker.Moniker;
import groovy.transform.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.CollectionUtils;

@EqualsAndHashCode(includes = "name")
@Data
@Builder
public class TencentInstance implements Instance, TencentBasicResource {

  private final String cloudProvider = TencentCloudProvider.ID;
  private final String providerType = TencentCloudProvider.ID;
  private String instanceName;
  private String account;
  private String name; // unique instance id
  private Long launchTime;
  private String zone;
  private TencentInstanceHealth instanceHealth;
  @Builder.Default private List<TencentTargetHealth> targetHealths = new ArrayList<>();
  private String vpcId;
  private String subnetId;
  @Builder.Default private List<String> privateIpAddresses = new ArrayList<>();
  @Builder.Default private List<String> publicIpAddresses = new ArrayList<>();
  private String instanceType;
  private String imageId;
  @Builder.Default private List<String> securityGroupIds = new ArrayList<>();
  @Builder.Default private List<Map<String, String>> tags = new ArrayList<>();

  private String serverGroupName;

  @Override
  public String getHumanReadableName() {
    return instanceName;
  }

  @Override
  @JsonIgnore
  public String getMonikerName() {
    return serverGroupName;
  }

  public List<Map<String, Object>> getHealth() {
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, Object>> healths = new ArrayList<>();
    if (instanceHealth != null) {
      healths.add(
          objectMapper.convertValue(instanceHealth, new TypeReference<Map<String, Object>>() {}));
    }
    if (!CollectionUtils.isEmpty(targetHealths)) {
      for (TencentTargetHealth targetHealth : targetHealths) {
        healths.add(
            objectMapper.convertValue(targetHealth, new TypeReference<Map<String, Object>>() {}));
      }
    }
    return healths;
  }

  @Override
  public HealthState getHealthState() {
    List<Health> health =
        new ArrayList<Health>() {
          {
            add(instanceHealth);
            addAll(targetHealths);
          }
        };
    return someUpRemainingUnknown(health)
        ? HealthState.Up
        : anyStarting(health)
            ? HealthState.Starting
            : anyDown(health)
                ? HealthState.Down
                : anyOutOfService(health) ? HealthState.OutOfService : HealthState.Unknown;
  }

  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(account)
        .withResource(TencentBasicResource.class)
        .deriveMoniker(this);
  }

  private static boolean someUpRemainingUnknown(List<Health> healthList) {
    if (CollectionUtils.isEmpty(healthList)) {
      return false;
    }
    List<Health> knownHealthList =
        healthList.stream()
            .filter(it -> it.getState() != HealthState.Unknown)
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(knownHealthList)) {
      return false;
    }

    return knownHealthList.stream()
        .allMatch(
            it -> {
              return it.getState() == HealthState.Up;
            });
  }

  private static boolean anyStarting(List<Health> healthList) {
    return healthList.stream().anyMatch(it -> it.getState() == HealthState.Starting);
  }

  private static boolean anyDown(List<Health> healthList) {
    return healthList.stream().anyMatch(it -> it.getState() == HealthState.Down);
  }

  private static boolean anyOutOfService(List<Health> healthList) {
    return healthList.stream().anyMatch(it -> it.getState() == HealthState.OutOfService);
  }
}
