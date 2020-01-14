package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentScheduledActionDescription;
import java.util.List;
import lombok.Data;

@Data
public class DeleteTencentScheduledActionAtomicOperation implements AtomicOperation<Void> {
  public DeleteTencentScheduledActionAtomicOperation(
      DeleteTencentScheduledActionDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String scheduledActionId = description.getScheduledActionId();
    String serverGroupName = description.getServerGroupName();
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete scheduled action "
                + scheduledActionId
                + " in "
                + serverGroupName
                + "...");

    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);
    client.deleteScheduledAction(scheduledActionId);
    getTask().updateStatus(BASE_PHASE, "Complete delete scheduled action. ");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static final String BASE_PHASE = "DELETE_SCHEDULED_ACTION";
  private DeleteTencentScheduledActionDescription description;
}
