package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentSecurityGroupDescription;
import groovy.util.logging.Slf4j;
import java.util.List;
import lombok.Data;

@Slf4j
@Data
public class DeleteTencentSecurityGroupAtomicOperation implements AtomicOperation<Void> {
  public DeleteTencentSecurityGroupAtomicOperation(
      DeleteTencentSecurityGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete of Tencent securityGroup "
                + getDescription().getSecurityGroupId()
                + "in "
                + getDescription().getRegion()
                + "...");

    VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String securityGroupId = description.getSecurityGroupId();
    getTask().updateStatus(BASE_PHASE, "Start delete securityGroup " + securityGroupId + " ...");
    vpcClient.deleteSecurityGroup(securityGroupId);
    getTask().updateStatus(BASE_PHASE, "Delete securityGroup " + securityGroupId + " end");

    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static final String BASE_PHASE = "DELETE_SECURITY_GROUP";
  private DeleteTencentSecurityGroupDescription description;
}
