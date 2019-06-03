package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import com.tencentcloudapi.as.v20180419.models.Instance;
import com.tencentcloudapi.clb.v20180317.models.Backend;
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend;
import com.tencentcloudapi.clb.v20180317.models.Target;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

@Slf4j
public abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  public abstract boolean isDisable();

  public abstract String getBasePhase();

  public AbstractEnableDisableAtomicOperation(
      EnableDisableTencentServerGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String basePhase = getBasePhase();

    getTask()
        .updateStatus(
            basePhase,
            "Initializing disable server group "
                + getDescription().getServerGroupName()
                + " in "
                + getDescription().getRegion()
                + "...");

    String serverGroupName = description.getServerGroupName();
    String region = description.getRegion();
    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);

    CloudVirtualMachineClient cvmClient =
        new CloudVirtualMachineClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);

    // find auto scaling group
    AutoScalingGroup asg = getAutoScalingGroup(client, serverGroupName);
    String asgId = asg.getAutoScalingGroupId();

    // enable or disable auto scaling group
    enableOrDisableAutoScalingGroup(client, asgId);

    // get in service instances in auto scaling group
    List<String> instanceIds = getAutoScalingInstances(client, asgId);

    if (CollectionUtils.isEmpty(instanceIds)) {
      getTask().updateStatus(basePhase, "Auto scaling group has no IN_SERVICE instance. ");
    } else {
      stopOrStartInstances(cvmClient, instanceIds);
    }

    // enable or disable load balancer
    if (ArrayUtils.isEmpty(asg.getLoadBalancerIdSet())
        && ArrayUtils.isEmpty(asg.getForwardLoadBalancerSet())) {
      getTask().updateStatus(basePhase, "Auto scaling group does not have a load balancer. ");
    } else {
      enableOrDisableClassicLoadBalancer(client, asg, instanceIds);
      try {
        // todo: the base method not been sig with tencentcloudsdkException, so we sallow the
        // exception
        enableOrDisableForwardLoadBalancer(client, asg, instanceIds);
      } catch (TencentCloudSDKException e) {
        log.error("operate error", e);
      }
    }

    getTask()
        .updateStatus(
            basePhase,
            "Complete disable/enable server group " + serverGroupName + " in " + region + ".");
    return null;
  }

  private AutoScalingGroup getAutoScalingGroup(AutoScalingClient client, String serverGroupName) {
    List<AutoScalingGroup> asgs = client.getAutoScalingGroupsByName(serverGroupName);
    if (!CollectionUtils.isEmpty(asgs)) {
      AutoScalingGroup asg = asgs.get(0);
      String asgId = asg.getAutoScalingGroupId();
      getTask()
          .updateStatus(
              getBasePhase(),
              "Server group " + serverGroupName + "\'s auto scaling group id is " + asgId);
      return asg;
    } else {
      getTask().updateStatus(getBasePhase(), "Server group " + serverGroupName + " is not found.");
      return null;
    }
  }

  private void enableOrDisableAutoScalingGroup(AutoScalingClient client, String asgId) {
    if (isDisable()) {
      getTask().updateStatus(getBasePhase(), "Disabling auto scaling group " + asgId + "...");
      client.disableAutoScalingGroup(asgId);
      getTask()
          .updateStatus(getBasePhase(), "Auto scaling group " + asgId + " status is disabled.");
    } else {
      getTask().updateStatus(getBasePhase(), "Enabling auto scaling group " + asgId + "...");
      client.enableAutoScalingGroup(asgId);
      getTask().updateStatus(getBasePhase(), "Auto scaling group " + asgId + " status is enabled.");
    }
  }

  private void stopOrStartInstances(CloudVirtualMachineClient client, List<String> instanceIds) {
    if (isDisable()) {
      getTask()
          .updateStatus(
              getBasePhase(), "stoping instances" + StringUtils.join(instanceIds, ',') + "...");
      client.stopInstances(instanceIds);
      getTask().updateStatus(getBasePhase(), "Complete stoping of instance.");
    } else {
      getTask()
          .updateStatus(
              getBasePhase(), "starting instances " + StringUtils.join(instanceIds, ',') + "...");
      client.startInstances(instanceIds);
      getTask()
          .updateStatus(
              getBasePhase(),
              "Complete starting instances " + StringUtils.join(instanceIds, ',') + "...");
    }
  }

  private List<String> getAutoScalingInstances(AutoScalingClient client, String asgId) {
    getTask().updateStatus(getBasePhase(), "Get instances managed by auto scaling group " + asgId);
    List<Instance> instances = client.getAutoScalingInstances(asgId);

    if (CollectionUtils.isEmpty(instances)) {
      getTask().updateStatus(getBasePhase(), "Found no instance in " + asgId + ".");
      return null;
    }

    if (isDisable()) {
      List<String> inServiceInstanceIds =
          instances.stream()
              .map(
                  it -> {
                    if (it.getHealthStatus().equals("HEALTHY")
                        && it.getLifeCycleState().equals("IN_SERVICE")) {
                      return it.getInstanceId();
                    } else {
                      return null;
                    }
                  })
              .collect(Collectors.toList());

      getTask()
          .updateStatus(
              getBasePhase(),
              "Auto scaling group "
                  + asgId
                  + " has InService instances "
                  + String.join(",", inServiceInstanceIds));
      return inServiceInstanceIds;
    } else {
      List<String> instanceIds =
          instances.stream()
              .map(
                  it -> {
                    if (it.getHealthStatus().equals("UNHEALTHY")
                        && !it.getLifeCycleState().equals("IN_SERVICE")) {
                      return it.getInstanceId();
                    } else {
                      return null;
                    }
                  })
              .collect(Collectors.toList());

      getTask()
          .updateStatus(
              getBasePhase(),
              "Auto scaling group "
                  + asgId
                  + " has unhealthy instances "
                  + String.join(",", instanceIds));
      return instanceIds;
    }
  }

  private void enableOrDisableClassicLoadBalancer(
      AutoScalingClient client, AutoScalingGroup asg, List<String> inServiceInstanceIds) {
    if (ArrayUtils.isEmpty(asg.getLoadBalancerIdSet())) {
      return;
    }

    String[] classicLbs = asg.getLoadBalancerIdSet();
    getTask()
        .updateStatus(
            getBasePhase(),
            "Auto scaling group is attached to classic load balancers "
                + String.join(",", classicLbs));

    for (String lbId : classicLbs) {
      if (isDisable()) {
        deregisterInstancesFromClassicalLb(client, lbId, inServiceInstanceIds);
      } else {
        registerInstancesWithClassicalLb(client, lbId, inServiceInstanceIds);
      }
    }
  }

  private void deregisterInstancesFromClassicalLb(
      AutoScalingClient client, String lbId, List<String> inServiceInstanceIds) {
    getTask()
        .updateStatus(
            getBasePhase(),
            "Start detach instances "
                + String.join(",", inServiceInstanceIds)
                + " from classic load balancers "
                + lbId);

    List<String> classicLbInstanceIds = client.getClassicLbInstanceIds(lbId);
    List<String> instanceIds =
        inServiceInstanceIds.stream()
            .filter(classicLbInstanceIds::contains)
            .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(instanceIds)) {
      getTask()
          .updateStatus(
              getBasePhase(),
              "Classic load balancer has instances "
                  + String.join(",", classicLbInstanceIds)
                  + "instances "
                  + String.join(",", instanceIds)
                  + " in both auto scaling group and load balancer will be detached from load balancer.");
      client.detachAutoScalingInstancesFromClassicClb(lbId, instanceIds);
    } else {
      getTask()
          .updateStatus(
              getBasePhase(),
              "Instances "
                  + String.join(",", inServiceInstanceIds)
                  + " are not attached with load balancer "
                  + lbId);
    }
    getTask()
        .updateStatus(
            getBasePhase(),
            "Finish detach instances "
                + String.join(",", inServiceInstanceIds)
                + " from classic load balancers "
                + lbId);
  }

  private void registerInstancesWithClassicalLb(
      AutoScalingClient client, String lbId, List<String> inServiceInstanceIds) {
    getTask()
        .updateStatus(
            getBasePhase(),
            "Start attach instances "
                + String.join(",", inServiceInstanceIds)
                + " to classic load balancers "
                + lbId);
    final List<Target> inServiceClassicTargets = new ArrayList<>();
    inServiceInstanceIds.stream()
        .forEach(
            instanceId -> {
              Target target = new Target();
              target.setInstanceId(instanceId);
              target.setWeight(10);
              inServiceClassicTargets.add(target);
            });
    client.attachAutoScalingInstancesToClassicClb(lbId, inServiceClassicTargets);
    getTask()
        .updateStatus(
            getBasePhase(),
            "Finish attach instances "
                + String.join(",", inServiceInstanceIds)
                + " to classic load balancers "
                + lbId);
  }

  private void enableOrDisableForwardLoadBalancer(
      AutoScalingClient client, AutoScalingGroup asg, List<String> inServiceInstanceIds)
      throws TencentCloudSDKException {
    if (ArrayUtils.isEmpty(asg.getForwardLoadBalancerSet())) {
      return;
    }

    ForwardLoadBalancer[] forwardLbs = asg.getForwardLoadBalancerSet();

    for (ForwardLoadBalancer flb : forwardLbs) {
      if (isDisable()) {
        deregisterInstancesFromForwardlLb(client, flb, inServiceInstanceIds);
      } else {
        registerInstancesWithForwardlLb(client, flb, inServiceInstanceIds);
      }
    }
  }

  private void deregisterInstancesFromForwardlLb(
      AutoScalingClient client, ForwardLoadBalancer flb, List<String> inServiceInstanceIds)
      throws TencentCloudSDKException {
    String flbId = flb.getLoadBalancerId();
    getTask()
        .updateStatus(
            getBasePhase(),
            "Start detach instances "
                + String.join(",", inServiceInstanceIds)
                + " from forward load balancers "
                + flbId);

    List<ListenerBackend> listeners = client.getForwardLbTargets(flb);
    final List<CompairableTarget> forwardLbTargets = new ArrayList();
    final List<CompairableTarget> inServiceTargets = new ArrayList();
    inServiceInstanceIds.forEach(
        instanceId -> {
          Arrays.stream(flb.getTargetAttributes())
              .forEach(
                  targetAttribute -> {
                    CompairableTarget target =
                        new CompairableTarget(
                            instanceId, targetAttribute.getPort(), targetAttribute.getWeight());
                    inServiceTargets.add(target);
                  });
        });

    listeners.stream()
        .forEach(
            it -> {
              // http and https
              if (it.getProtocol().equals("HTTP") || it.getProtocol().equals("HTTPS")) {
                Arrays.stream(it.getRules())
                    .forEach(
                        rule -> {
                          if (rule.getLocationId().equals(flb.getLocationId())) {
                            for (Backend flbTarget : rule.getTargets()) {
                              CompairableTarget target =
                                  new CompairableTarget(
                                      flbTarget.getInstanceId(),
                                      flbTarget.getPort(),
                                      flbTarget.getWeight());
                              forwardLbTargets.add(target);
                            }
                          }
                        }); // ends rules foreach
              } else if (it.getProtocol().equals("TCP") || it.getProtocol().equals("UDP")) {
                for (Backend flbTarget : it.getTargets()) {
                  CompairableTarget target =
                      new CompairableTarget(
                          flbTarget.getInstanceId(), flbTarget.getPort(), flbTarget.getWeight());
                  forwardLbTargets.add(target);
                }
              } else {
                return;
              }
            });

    List<CompairableTarget> targets =
        inServiceTargets.stream().filter(forwardLbTargets::contains).collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(targets)) {
      getTask()
          .updateStatus(
              getBasePhase(),
              "Forward load balancer has targets "
                  + Arrays.toString(forwardLbTargets.toArray())
                  + Arrays.toString(targets.toArray())
                  + " in both auto scaling group and load balancer will be detached from load balancer "
                  + flbId);
      client.detachAutoScalingInstancesFromForwardClb(flb, targets, true);
    } else {
      getTask()
          .updateStatus(
              getBasePhase(),
              "Instances "
                  + Arrays.toString(inServiceInstanceIds.toArray())
                  + " are not attached with load balancer "
                  + flbId);
    }

    getTask()
        .updateStatus(
            getBasePhase(),
            "Finish detach instances "
                + Arrays.toString(inServiceInstanceIds.toArray())
                + " from forward load balancers "
                + flbId);
  }

  private void registerInstancesWithForwardlLb(
      AutoScalingClient client, final ForwardLoadBalancer flb, List<String> inServiceInstanceIds)
      throws TencentCloudSDKException {
    String flbId = flb.getLoadBalancerId();
    getTask()
        .updateStatus(
            getBasePhase(),
            "Start attach instances "
                + Arrays.toString(inServiceInstanceIds.toArray())
                + " from forward load balancers "
                + flbId);

    final List<CompairableTarget> inServiceTargets = new ArrayList<>();
    inServiceInstanceIds.stream()
        .forEach(
            instanceId -> {
              Arrays.stream(flb.getTargetAttributes())
                  .forEach(
                      targetAttribute -> {
                        CompairableTarget target =
                            new CompairableTarget(
                                instanceId, targetAttribute.getPort(), targetAttribute.getWeight());
                        inServiceTargets.add(target);
                      });
            });

    if (!CollectionUtils.isEmpty(inServiceTargets)) {
      getTask()
          .updateStatus(
              getBasePhase(),
              "In service targets "
                  + Arrays.toString(inServiceTargets.toArray())
                  + " will be attached to forward load balancer "
                  + flbId);
      client.attachAutoScalingInstancesToForwardClb(flb, inServiceTargets, true);
    } else {
      getTask()
          .updateStatus(
              getBasePhase(), "No instances need to be attached to forward load balancer " + flbId);
    }
    getTask()
        .updateStatus(
            getBasePhase(),
            "Finish attach instances "
                + Arrays.toString(inServiceInstanceIds.toArray())
                + " from forward load balancers "
                + flbId);
  }

  public static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public EnableDisableTencentServerGroupDescription getDescription() {
    return description;
  }

  public void setDescription(EnableDisableTencentServerGroupDescription description) {
    this.description = description;
  }

  private EnableDisableTencentServerGroupDescription description;

  @EqualsAndHashCode(callSuper = false)
  @AllArgsConstructor
  @ToString
  static class CompairableTarget extends Target {
    private String instanceId;
    private Integer weight;
    private Integer port;
  }
}
