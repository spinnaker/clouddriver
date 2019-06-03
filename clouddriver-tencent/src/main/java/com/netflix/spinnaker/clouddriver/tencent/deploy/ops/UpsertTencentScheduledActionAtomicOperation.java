package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public class UpsertTencentScheduledActionAtomicOperation implements AtomicOperation<Void> {
  public UpsertTencentScheduledActionAtomicOperation(
      UpsertTencentScheduledActionDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    String accountName = description.getAccountName();
    String asgId = tencentClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region);

    if (StringUtils.isEmpty(asgId)) {
      throw new TencentOperationException("ASG of " + serverGroupName + " is not found.");
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert scheduled action " + serverGroupName + " in " + region + "...");

    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);

    if (description
        .getOperationType()
        .equals(UpsertTencentScheduledActionDescription.OperationType.CREATE)) {
      getTask().updateStatus(BASE_PHASE, "create scheduled action in " + serverGroupName + "...");
      String scalingPolicyId = client.createScheduledAction(asgId, description);
      getTask()
          .updateStatus(BASE_PHASE, "new scheduled action " + scalingPolicyId + " is created.");
    } else if (description
        .getOperationType()
        .equals(UpsertTencentScheduledActionDescription.OperationType.MODIFY)) {
      String scheduledActionId = description.getScheduledActionId();
      getTask()
          .updateStatus(
              BASE_PHASE,
              "update scheduled action " + scheduledActionId + " in " + serverGroupName + "...");
      client.modifyScheduledAction(scheduledActionId, description);
    } else {
      throw new TencentOperationException("unknown operation type, operation quit.");
    }

    getTask().updateStatus(BASE_PHASE, "Complete upsert scheduled action.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertTencentScheduledActionDescription getDescription() {
    return description;
  }

  public void setDescription(UpsertTencentScheduledActionDescription description) {
    this.description = description;
  }

  public TencentClusterProvider getTencentClusterProvider() {
    return tencentClusterProvider;
  }

  public void setTencentClusterProvider(TencentClusterProvider tencentClusterProvider) {
    this.tencentClusterProvider = tencentClusterProvider;
  }

  private static final String BASE_PHASE = "UPSERT_SCHEDULED_ACTIONS";
  private UpsertTencentScheduledActionDescription description;
  @Autowired private TencentClusterProvider tencentClusterProvider;
}
