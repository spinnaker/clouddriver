package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateAndDecrementTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class TerminateAndDecrementTencentServerGroupAtomicOperation
    implements AtomicOperation<Void> {
  public TerminateAndDecrementTencentServerGroupAtomicOperation(
      TerminateAndDecrementTencentServerGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    List<String> instanceIds = new ArrayList<String>(Arrays.asList(description.getInstance()));
    String accountName = description.getCredentials().getName();

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing termination of instance ("
                + getDescription().getInstance()
                + ") in "
                + getDescription().getRegion()
                + ":"
                + serverGroupName
                + " and decrease server group desired capacity...");

    String asgId = tencentClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region);
    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);
    client.removeInstances(asgId, instanceIds);
    getTask()
        .updateStatus(
            BASE_PHASE, "Complete terminate instance and decrease server group desired capacity.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public TencentClusterProvider getTencentClusterProvider() {
    return tencentClusterProvider;
  }

  public void setTencentClusterProvider(TencentClusterProvider tencentClusterProvider) {
    this.tencentClusterProvider = tencentClusterProvider;
  }

  public TerminateAndDecrementTencentServerGroupDescription getDescription() {
    return description;
  }

  public void setDescription(TerminateAndDecrementTencentServerGroupDescription description) {
    this.description = description;
  }

  private static final String BASE_PHASE = "TERMINATE_AND_DEC_INSTANCES";
  @Autowired private TencentClusterProvider tencentClusterProvider;
  private TerminateAndDecrementTencentServerGroupDescription description;
}
