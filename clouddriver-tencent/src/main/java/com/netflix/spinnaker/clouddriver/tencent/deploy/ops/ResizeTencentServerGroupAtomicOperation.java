package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.ResizeTencentServerGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class ResizeTencentServerGroupAtomicOperation implements AtomicOperation<Void> {
  public ResizeTencentServerGroupAtomicOperation(ResizeTencentServerGroupDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing resize of server group "
                + description.getServerGroupName()
                + " in "
                + description.getRegion()
                + "...");
    String accountName = description.getAccountName();
    String region = description.getRegion();
    String serverGroupName = description.getServerGroupName();
    String asgId = tencentClusterProvider.getServerGroupAsgId(serverGroupName, accountName, region);

    AutoScalingClient client =
        new AutoScalingClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            region);
    client.resizeAutoScalingGroup(asgId, description.getCapacity());
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Complete resize of server group "
                + description.getServerGroupName()
                + " in "
                + description.getRegion()
                + ".");
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

  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP";
  @Autowired private TencentClusterProvider tencentClusterProvider;
  private final ResizeTencentServerGroupDescription description;
}
