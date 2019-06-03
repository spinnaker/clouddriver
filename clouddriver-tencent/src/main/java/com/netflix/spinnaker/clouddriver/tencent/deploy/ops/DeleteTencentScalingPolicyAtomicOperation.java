package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScalingPolicyDescription;
import java.util.List;
import lombok.Data;

@Data
public class DeleteTencentScalingPolicyAtomicOperation implements AtomicOperation<Void> {
  public DeleteTencentScalingPolicyAtomicOperation(
      DeleteTencentScalingPolicyDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String scalingPolicyId = description.getScalingPolicyId();
    String serverGroupName = description.getServerGroupName();

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete scaling policy "
                + scalingPolicyId
                + " in "
                + serverGroupName
                + "...");
    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);
    client.deleteScalingPolicy(scalingPolicyId);
    getTask().updateStatus(BASE_PHASE, "Complete delete scaling policy. ");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static final String BASE_PHASE = "DELETE_SCALING_POLICY";
  private DeleteTencentScalingPolicyDescription description;
}
