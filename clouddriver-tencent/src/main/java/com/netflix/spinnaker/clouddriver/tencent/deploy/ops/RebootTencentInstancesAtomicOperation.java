package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.RebootTencentInstancesDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class RebootTencentInstancesAtomicOperation implements AtomicOperation<Void> {
  public RebootTencentInstancesAtomicOperation(RebootTencentInstancesDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    List<String> instanceIds = description.getInstanceIds();

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing reboot of instances ("
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
    client.rebootInstances(instanceIds);
    getTask().updateStatus(BASE_PHASE, "Complete reboot of instance.");
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public RebootTencentInstancesDescription getDescription() {
    return description;
  }

  public void setDescription(RebootTencentInstancesDescription description) {
    this.description = description;
  }

  public TencentClusterProvider getTencentClusterProvider() {
    return tencentClusterProvider;
  }

  public void setTencentClusterProvider(TencentClusterProvider tencentClusterProvider) {
    this.tencentClusterProvider = tencentClusterProvider;
  }

  private static final String BASE_PHASE = "REBOOT_INSTANCES";
  private RebootTencentInstancesDescription description;
  @Autowired private TencentClusterProvider tencentClusterProvider;
}
