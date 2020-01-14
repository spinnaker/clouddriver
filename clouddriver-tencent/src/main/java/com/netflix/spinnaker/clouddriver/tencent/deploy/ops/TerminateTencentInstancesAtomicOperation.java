package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.TerminateTencentInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class TerminateTencentInstancesAtomicOperation implements AtomicOperation<Void> {
  public TerminateTencentInstancesAtomicOperation(
      TerminateTencentInstancesDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    List<String> instanceIds = description.getInstanceIds();
    String accountName = description.getAccountName();

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing termination of instances ("
                + String.join(",", getDescription().getInstanceIds())
                + ") in "
                + getDescription().getRegion()
                + ":"
                + serverGroupName
                + "...");

    CloudVirtualMachineClient client =
        new CloudVirtualMachineClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);
    client.terminateInstances(instanceIds);

    getTask().updateStatus(BASE_PHASE, "Complete termination of instance.");
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

  public TerminateTencentInstancesDescription getDescription() {
    return description;
  }

  public void setDescription(TerminateTencentInstancesDescription description) {
    this.description = description;
  }

  private static final String BASE_PHASE = "TERMINATE_INSTANCES";
  @Autowired private TencentClusterProvider tencentClusterProvider;
  private TerminateTencentInstancesDescription description;
}
