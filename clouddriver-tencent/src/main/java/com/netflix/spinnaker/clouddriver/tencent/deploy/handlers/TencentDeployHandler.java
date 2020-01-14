package com.netflix.spinnaker.clouddriver.tencent.deploy.handlers;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.TencentServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TencentDeployDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentServerGroup;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.AutoScalingNotification;
import com.tencentcloudapi.as.v20180419.models.ScalingPolicy;
import com.tencentcloudapi.as.v20180419.models.ScheduledAction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TencentDeployHandler implements DeployHandler<TencentDeployDescription> {
  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof TencentDeployDescription;
  }

  @Override
  public DeploymentResult handle(final TencentDeployDescription description, List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE, "Initializing deployment to " + String.join(",", description.getZones()));

    String accountName = description.getAccountName();
    String region = description.getRegion();
    TencentServerGroupNameResolver serverGroupNameResolver =
        new TencentServerGroupNameResolver(
            accountName, region, tencentClusterProvider, description.getCredentials());

    getTask().updateStatus(BASE_PHASE, "Looking up next sequence...");

    String serverGroupName =
        serverGroupNameResolver.resolveNextServerGroupName(
            description.getApplication(),
            description.getStack(),
            description.getFreeFormDetails(),
            false);

    getTask().updateStatus(BASE_PHASE, "Produced server group name: " + serverGroupName);

    description.setServerGroupName(serverGroupName);

    AutoScalingClient autoScalingClient =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);

    if (description.getSource() != null && description.getSource().getUseSourceCapacity() != null) {
      log.info("copy source server group capacity");
      String sourceServerGroupName = description.getSource().getServerGroupName();
      String sourceRegion = description.getSource().getRegion();
      TencentServerGroup sourceServerGroup =
          tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName);
      if (sourceServerGroup == null) {
        log.warn("source server group " + sourceServerGroupName + " is not found");
      } else {
        description.setDesiredCapacity((Integer) sourceServerGroup.getAsg().get("desiredCapacity"));
        description.setMaxSize((Integer) sourceServerGroup.getAsg().get("maxSize"));
        description.setMinSize((Integer) sourceServerGroup.getAsg().get("minSize"));
      }
    }

    getTask().updateStatus(BASE_PHASE, "Composing server group " + serverGroupName + "...");

    autoScalingClient.deploy(description);

    getTask()
        .updateStatus(
            BASE_PHASE, "Done creating server group " + serverGroupName + " in " + region + ".");

    DeploymentResult deploymentResult = new DeploymentResult();
    deploymentResult.setServerGroupNames(Arrays.asList(region + ":" + serverGroupName));
    deploymentResult.getServerGroupNameByRegion().put(region, serverGroupName);

    if (description.isCopySourceScalingPoliciesAndActions()) {
      copyScalingPolicyAndScheduledAction(description, deploymentResult);
      copyNotification(description, deploymentResult); // copy notification by the way
    }
    return deploymentResult;
  }

  private void copyNotification(
      TencentDeployDescription description, DeploymentResult deployResult) {
    getTask().updateStatus(BASE_PHASE, "Enter copyNotification.");
    String sourceServerGroupName = description.getSource().getServerGroupName();
    String sourceRegion = description.getSource().getRegion();
    String accountName = description.getAccountName();
    TencentServerGroup sourceServerGroup =
        tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName);

    if (sourceServerGroup == null) {
      log.warn(
          "source server group not found, account "
              + accountName
              + ", region "
              + sourceRegion
              + ", source sg name "
              + sourceServerGroupName);
      return;
    }
    String sourceAsgId = (String) sourceServerGroup.getAsg().get("autoScalingGroupId");

    getTask().updateStatus(BASE_PHASE, "Initializing copy notification from " + sourceAsgId);

    AutoScalingClient autoScalingClient =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            sourceRegion);

    String newServerGroupName = deployResult.getServerGroupNameByRegion().get("sourceRegion");
    AutoScalingGroup newAsg =
        autoScalingClient.getAutoScalingGroupsByName(newServerGroupName).get(0);
    String newAsgId = newAsg.getAutoScalingGroupId();

    List<AutoScalingNotification> notifications = autoScalingClient.getNotification(sourceAsgId);
    for (AutoScalingNotification notification : notifications) {
      try {
        autoScalingClient.createNotification(newAsgId, notification);
      } catch (TencentOperationException toe) {
        // something bad happened during creation, log the error and continue
        log.warn("create notification error ", toe);
      }
    }
  }

  private void copyScalingPolicyAndScheduledAction(
      TencentDeployDescription description, DeploymentResult deployResult) {
    getTask().updateStatus(BASE_PHASE, "Enter copyScalingPolicyAndScheduledAction.");

    String sourceServerGroupName = description.getSource().getServerGroupName();
    String sourceRegion = description.getSource().getRegion();
    String accountName = description.getAccountName();
    TencentServerGroup sourceServerGroup =
        tencentClusterProvider.getServerGroup(accountName, sourceRegion, sourceServerGroupName);

    if (sourceServerGroup == null) {
      log.warn("description is " + description);
      log.warn(
          "source server group not found, account "
              + accountName
              + ", region "
              + sourceRegion
              + ", source sg name "
              + sourceServerGroupName);
      return;
    }

    String sourceAsgId = (String) sourceServerGroup.getAsg().get("autoScalingGroupId");

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing copy scaling policy and scheduled action from " + sourceAsgId + ".");

    AutoScalingClient autoScalingClient =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            sourceRegion);

    String newServerGroupName = deployResult.getServerGroupNameByRegion().get("sourceRegion");
    AutoScalingGroup newAsg =
        autoScalingClient.getAutoScalingGroupsByName(newServerGroupName).get(0);
    String newAsgId = newAsg.getAutoScalingGroupId();

    // copy all scaling policies
    List<ScalingPolicy> scalingPolicies = autoScalingClient.getScalingPolicies(sourceAsgId);
    for (ScalingPolicy scalingPolicy : scalingPolicies) {
      UpsertTencentScalingPolicyDescription scalingPolicyDescription =
          new UpsertTencentScalingPolicyDescription();
      scalingPolicyDescription.setServerGroupName(newServerGroupName);
      scalingPolicyDescription.setRegion(sourceRegion);
      scalingPolicyDescription.setAccountName(accountName);
      scalingPolicyDescription.setOperationType(
          UpsertTencentScalingPolicyDescription.OperationType.CREATE);
      scalingPolicyDescription.setAdjustmentType(scalingPolicy.getAdjustmentType());
      scalingPolicyDescription.setAdjustmentValue(
          Integer.valueOf(scalingPolicy.getAdjustmentValue()));
      scalingPolicyDescription.setMetricAlarm(scalingPolicy.getMetricAlarm());
      // it.notificationUserGroupIds = scalingPolicy.notificationUserGroupIds
      scalingPolicyDescription.setCooldown(scalingPolicy.getCooldown());
      autoScalingClient.createScalingPolicy(newAsgId, scalingPolicyDescription);
    }

    // copy all scheduled actions
    List<ScheduledAction> scheduledActions = autoScalingClient.getScheduledAction(sourceAsgId);
    for (ScheduledAction scheduledAction : scheduledActions) {
      LocalDateTime original_start_time =
          LocalDateTime.parse(
              scheduledAction.getStartTime(),
              DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+08:00'"));
      LocalDateTime current_time = LocalDateTime.now();
      LocalDateTime new_start_time = original_start_time;

      if (original_start_time.isBefore(current_time)) {
        log.info("original start time is before current time");
        if (scheduledAction.getEndTime() == "0000-00-00T00:00:00+08:00") {
          // schedule action just run for once, and had finished
          continue;
        } else {
          log.info("scheduled action is for once, set new start time to current time");
          new_start_time = current_time.plusMinutes(60);
        }
      } else {
        log.info("scheduled action is not trigger, use original start time");
        new_start_time = original_start_time;
      }

      UpsertTencentScheduledActionDescription scheduledActionDescription =
          new UpsertTencentScheduledActionDescription();
      scheduledActionDescription.setServerGroupName(newServerGroupName);
      scheduledActionDescription.setRegion(sourceRegion);
      scheduledActionDescription.setAccountName(accountName);
      scheduledActionDescription.setOperationType(
          UpsertTencentScheduledActionDescription.OperationType.CREATE);
      scheduledActionDescription.setMaxSize(scheduledAction.getMaxSize());
      scheduledActionDescription.setMinSize(scheduledAction.getMinSize());
      scheduledActionDescription.setDesiredCapacity(scheduledAction.getDesiredCapacity());
      scheduledActionDescription.setStartTime(
          new_start_time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+08:00'")));
      scheduledActionDescription.setEndTime(scheduledAction.getEndTime());
      scheduledActionDescription.setRecurrence(scheduledAction.getRecurrence());
      autoScalingClient.createScheduledAction(newAsgId, scheduledActionDescription);
    }
  }

  private static final String BASE_PHASE = "DEPLOY";
  @Autowired private TencentClusterProvider tencentClusterProvider;
}
