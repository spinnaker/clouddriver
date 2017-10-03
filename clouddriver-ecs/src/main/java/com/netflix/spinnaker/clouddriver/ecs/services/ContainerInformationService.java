/*
 *
 *  * Copyright 2017 Lookout, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.services;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesResult;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.InvalidParameterException;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkBinding;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContainerInformationService {

  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  private AmazonClientProvider amazonClientProvider;


  public List<Map<String, String>> getHealthStatus(String clusterArn, String taskId, String serviceArn, String accountName, String region) {
    // TODO - remove the cheese here

    NetflixAmazonCredentials accountCredentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(accountName);
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(accountName, accountCredentials.getCredentialsProvider(), region);

    DescribeServicesResult describeServicesResult = amazonECS.describeServices(new DescribeServicesRequest().withServices(serviceArn).withCluster(clusterArn));

    Service service = describeServicesResult.getServices().get(0);
    if (service.getLoadBalancers().size() == 1) {
      String loadBalancerName = service.getLoadBalancers().get(0).getLoadBalancerName();

      AmazonElasticLoadBalancing AmazonloadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(accountName, accountCredentials.getCredentialsProvider(), region);
      AmazonloadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest().withNames(loadBalancerName));

      DescribeTasksResult taskDescription = amazonECS.describeTasks(new DescribeTasksRequest().withCluster(service.getClusterArn()).withTasks(taskId));
      DescribeContainerInstancesResult containerDescriptions = amazonECS.describeContainerInstances(
        new DescribeContainerInstancesRequest().withCluster(clusterArn).withContainerInstances(taskDescription.getTasks().get(0).getContainerInstanceArn()));
      // TODO: Currently assuming there's 1 container with 1 port for the task given.
      int port = taskDescription.getTasks().get(0).getContainers().get(0).getNetworkBindings().get(0).getHostPort();

      DescribeTargetHealthResult targetGroupHealthResult = null;
      List<LoadBalancer> loadBalancers = service.getLoadBalancers();
      //There should only be 1 based on AWS documentation.
      if(loadBalancers.size()>1){
        throw new IllegalArgumentException("Cannot have more than 1 loadbalancer while checking ECS health.");
      }
      for (LoadBalancer loadBalancer : loadBalancers) {
        targetGroupHealthResult = AmazonloadBalancing.describeTargetHealth(
          new DescribeTargetHealthRequest().withTargetGroupArn(loadBalancer.getTargetGroupArn()).withTargets(
            new TargetDescription().withId(containerDescriptions.getContainerInstances().get(0).getEc2InstanceId()).withPort(port)));
      }

      List<Map<String, String>> healthMetrics = new ArrayList<>();
      Map<String, String> loadBalancerHealth = new HashMap<>();
      loadBalancerHealth.put("instanceId", taskId);

      String targetHealth = targetGroupHealthResult.getTargetHealthDescriptions().get(0).getTargetHealth().getState();
      loadBalancerHealth.put("state", targetHealth.equals("healthy") ? "Up" : "Unknown");  // TODO - Return better values, and think of a better strategy at defining health
      loadBalancerHealth.put("type", "loadBalancer");

      healthMetrics.add(loadBalancerHealth);
      return healthMetrics;
    } else {
      return null;
    }

  }

  public String getClusterArn(AmazonECS amazonECS, String taskId) {
    List<String> taskIds = new ArrayList<>();
    taskIds.add(taskId);
    for (String clusterArn : amazonECS.listClusters().getClusterArns()) {
      DescribeTasksRequest request = new DescribeTasksRequest().withCluster(clusterArn).withTasks(taskIds);
      if (!amazonECS.describeTasks(request).getTasks().isEmpty()) {
        return clusterArn;
      }
    }
    return null;
  }

  public String getTaskPrivateAddress(AmazonECS amazonECS, AmazonEC2 amazonEC2, Task task) {
    List<Container> containers = task.getContainers();
    if (containers == null || containers.size() <1) {
      return "unknown";
    }

    List<NetworkBinding> networkBindings = containers.get(0).getNetworkBindings();
    if (networkBindings == null || networkBindings.size() <1) {
      return "unknown";
    }

    int hostPort = networkBindings.get(0).getHostPort();
    String hostEc2InstanceId = getContainerInstance(amazonECS, task).getEc2InstanceId();

    DescribeInstancesResult describeInstancesResult = amazonEC2.describeInstances(new DescribeInstancesRequest().withInstanceIds(hostEc2InstanceId));
    String hostPrivateIpAddress = describeInstancesResult.getReservations().get(0).getInstances().get(0).getPrivateIpAddress(); // TODO - lots of assumptions are made here and need to be relaxed.  get(0) are probably all no-no's

    return String.format("%s:%s", hostPrivateIpAddress, hostPort);
  }

  public ContainerInstance getContainerInstance(AmazonECS amazonECS, Task task) {
    if (task == null) {
      return null;
    }

    ContainerInstance container = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(task.getContainerInstanceArn());
    DescribeContainerInstancesRequest request = new DescribeContainerInstancesRequest()
      .withCluster(task.getClusterArn())
      .withContainerInstances(queryList);
    List<ContainerInstance> containerList = amazonECS.describeContainerInstances(request).getContainerInstances();

    if (!containerList.isEmpty()) {
      if (containerList.size() != 1) {
        throw new InvalidParameterException("Tasks should only have one container associated to them. Multiple found");
      }
      container = containerList.get(0);
    }

    return container;
  }

  public InstanceStatus getEC2InstanceStatus(AmazonEC2 amazonEC2, ContainerInstance container) {
    if (container == null) {
      return null;
    }

    InstanceStatus instanceStatus = null;

    List<String> queryList = new ArrayList<>();
    queryList.add(container.getEc2InstanceId());
    DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest()
      .withInstanceIds(queryList);
    List<InstanceStatus> instanceStatusList = amazonEC2.describeInstanceStatus(request).getInstanceStatuses();

    if (!instanceStatusList.isEmpty()) {
      if (instanceStatusList.size() != 1) {
        String message = "Container instances should only have only one Instance Status. Multiple found";
        throw new InvalidParameterException(message);
      }
      instanceStatus = instanceStatusList.get(0);
    }

    return instanceStatus;
  }

  public String getClusterName(String serviceName, String accountName, String region) {
    NetflixAmazonCredentials accountCredentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(accountName);
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(accountName, accountCredentials.getCredentialsProvider(), region);

    ListClustersResult listClustersResult = amazonECS.listClusters();

    for (String clusterARN : listClustersResult.getClusterArns()) {
      DescribeServicesResult describeServicesResult = amazonECS.describeServices(new DescribeServicesRequest().withServices(serviceName).withCluster(clusterARN));
      for (Service service : describeServicesResult.getServices()) {
        if (service.getServiceName().equals(serviceName)) {
          return StringUtils.substringAfterLast(clusterARN, "/");
        }
      }
    }

    return null;
  }

  public int getLatestServiceVersion(String clusterName, String serviceName, String accountName, String region) {
    int latestVersion = 0;

    NetflixAmazonCredentials accountCredentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(accountName);
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(accountName, accountCredentials.getCredentialsProvider(), region);

    ListServicesResult listServicesResult = amazonECS.listServices(new ListServicesRequest().withCluster(clusterName));

    for (String serviceARN : listServicesResult.getServiceArns()) {
      if (!serviceARN.contains(serviceName)) { continue; }

      int currentVersion = 0;
      try {
        currentVersion = Integer.parseInt(StringUtils.substringAfterLast(serviceARN, "-").replaceAll("v", ""));
      } catch (NumberFormatException e) {
      }
      latestVersion = Math.max(currentVersion, latestVersion);
    }

    return latestVersion;
  }
}
