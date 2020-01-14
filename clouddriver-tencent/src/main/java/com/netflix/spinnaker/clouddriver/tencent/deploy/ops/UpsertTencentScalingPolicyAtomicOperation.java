package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public class UpsertTencentScalingPolicyAtomicOperation implements AtomicOperation<Void> {
  public UpsertTencentScalingPolicyAtomicOperation(
      UpsertTencentScalingPolicyDescription description) {
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
            "Initializing upsert scaling policy " + serverGroupName + " in " + region + "...");

    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);

    if (description
        .getOperationType()
        .equals(UpsertTencentScalingPolicyDescription.OperationType.CREATE)) {
      getTask().updateStatus(BASE_PHASE, "create scaling policy in " + serverGroupName + "...");
      String scalingPolicyId = client.createScalingPolicy(asgId, description);
      getTask().updateStatus(BASE_PHASE, "new scaling policy " + scalingPolicyId + " is created.");
    } else if (description
        .getOperationType()
        .equals(UpsertTencentScalingPolicyDescription.OperationType.MODIFY)) {
      String scalingPolicyId = description.getScalingPolicyId();
      getTask()
          .updateStatus(
              BASE_PHASE,
              "update scaling policy " + scalingPolicyId + " in " + serverGroupName + "...");
      client.modifyScalingPolicy(scalingPolicyId, description);
    } else {
      throw new TencentOperationException("unknown operation type, operation quit.");
    }

    getTask().updateStatus(BASE_PHASE, "Complete upsert scaling policy.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertTencentScalingPolicyDescription getDescription() {
    return description;
  }

  public void setDescription(UpsertTencentScalingPolicyDescription description) {
    this.description = description;
  }

  public TencentClusterProvider getTencentClusterProvider() {
    return tencentClusterProvider;
  }

  public void setTencentClusterProvider(TencentClusterProvider tencentClusterProvider) {
    this.tencentClusterProvider = tencentClusterProvider;
  }

  private static final String BASE_PHASE = "UPSERT_SCALING_POLICY";
  private UpsertTencentScalingPolicyDescription description;
  @Autowired private TencentClusterProvider tencentClusterProvider;
}
