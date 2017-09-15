package com.netflix.spinnaker.clouddriver.ecs.view;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
public class EcsInstanceProvider implements InstanceProvider<EcsTask> {

  private final String cloudProvider = EcsCloudProvider.ID;

  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  private AmazonClientProvider amazonClientProvider;

  @Autowired
  private ContainerInformationService containerInformationService;

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public EcsTask getInstance(String account, String region, String id) {
    if (!isValidId(id, region))
      return null;

    EcsTask ecsInstance = null;

    NetflixAmazonCredentials netflixAmazonCredentials =
      (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(account);
    AWSCredentialsProvider awsCredentialsProvider = netflixAmazonCredentials.getCredentialsProvider();
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(account, awsCredentialsProvider, region);
    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(account, awsCredentialsProvider, region);

    Task ecsTask = getTask(amazonECS, id);
    InstanceStatus instanceStatus = containerInformationService.getEC2InstanceStatus(amazonEC2, containerInformationService.getContainerInstance(amazonECS, ecsTask));

    if (ecsTask != null && instanceStatus != null) {
//      List<Map<String, String>> healthStatus = containerInformationService.getHealthStatus("poc", ecsTask.getTaskArn(), null, "continuous-delivery", "us-west-2");  // TODO - Use the caching system properly
      ecsInstance = new EcsTask(id, ecsTask, instanceStatus, null);
    }

    return ecsInstance;
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }

  private List<String> getAllClusters(AmazonECS amazonECS) {
    ListClustersResult listClustersResult = amazonECS.listClusters();
    List<String> clusterList = listClustersResult.getClusterArns();
    while (listClustersResult.getNextToken() != null) {
      listClustersResult = amazonECS.listClusters(
        new ListClustersRequest().withNextToken(listClustersResult.getNextToken())
      );
      clusterList.addAll(listClustersResult.getClusterArns());
    }
    return clusterList;
  }

  private boolean isValidId(String id, String region) {
    String id_regex = "[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}";
    String id_only = String.format("^%s$", id_regex);
    String arn = String.format("arn:aws:ecs:%s:\\d*:task/%s", region, id_regex);
    return id.matches(id_only) || id.matches(arn);
  }

  private Task getTask(AmazonECS amazonECS, String taskId) {
    Task task = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(taskId);

    for (String cluster: getAllClusters(amazonECS)) {
      DescribeTasksRequest request = new DescribeTasksRequest()
        .withCluster(cluster)
        .withTasks(queryList);
      List<Task> taskList = amazonECS.describeTasks(request).getTasks();
      if (!taskList.isEmpty()) {
        if (taskList.size() != 1) {
          String message = String.format("Task ID: %s should only match one record. Multiple found.", taskId);
          throw new InvalidParameterException(message);
        }
        task = taskList.get(0);
        break;
      }
    }
    return task;
  }

}

