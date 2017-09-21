/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.InstanceStatus;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.DescribeTasksResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.model.TaskDefinition;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class EcsServerClusterProvider implements ClusterProvider<EcsServerCluster> {

  private AccountCredentialsProvider accountCredentialsProvider;
  private AmazonClientProvider amazonClientProvider;
  private ContainerInformationService containerInformationService;

  @Autowired
  public EcsServerClusterProvider(AccountCredentialsProvider accountCredentialsProvider, AmazonClientProvider amazonClientProvider, ContainerInformationService containerInformationService) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    this.containerInformationService = containerInformationService;
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusters() {
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials && credentials.getCloudProvider().equals(EcsCloudProvider.ID)) {  // TODO - the first if condition can be problematic for AWS logic.  We'll probably need to look into this after POC, and figure out how to deal with account credentials for AWS and ECS in a smart way, without AWS trying to use ECS credentials
        clusterMap = findClusters(clusterMap, (AmazonCredentials) credentials);
      }
    }
    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClusters(Map<String, Set<EcsServerCluster>> clusterMap,
                                                          AmazonCredentials credentials) {
    for (AmazonCredentials.AWSRegion awsRegion: credentials.getRegions()) {
      clusterMap = findClustersForRegion(clusterMap, credentials, awsRegion);
    }

    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClustersForRegion(Map<String, Set<EcsServerCluster>> clusterMap,
                                                                   AmazonCredentials credentials,
                                                                   AmazonCredentials.AWSRegion awsRegion) {

    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(credentials.getName(),
      credentials.getCredentialsProvider(),
      awsRegion.getName());
    AmazonEC2 amazonEC2 = amazonClientProvider.getAmazonEC2(credentials.getName(),
      credentials.getCredentialsProvider(),
      awsRegion.getName());
    AmazonElasticLoadBalancing amazonELB = amazonClientProvider.getAmazonElasticLoadBalancingV2(credentials.getName(),
      credentials.getCredentialsProvider(),
      awsRegion.getName());

    for (String clusterArn: amazonECS.listClusters().getClusterArns()) {

      ListServicesResult result = amazonECS.listServices(new ListServicesRequest().withCluster(clusterArn));
      for (String serviceArn: result.getServiceArns()) {

        ServiceMetadata metadata = extractMetadataFromServiceArn(serviceArn);
        Set<Instance> instances = new HashSet<>();

        DescribeLoadBalancersResult loadBalancersResult = amazonELB.describeLoadBalancers(
          new DescribeLoadBalancersRequest());
        Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> loadBalancers
          = extractLoadBalancersData(loadBalancersResult);

        ListTasksResult listTasksResult = amazonECS.listTasks(
          new ListTasksRequest().withServiceName(serviceArn).withCluster(clusterArn));
        if (listTasksResult.getTaskArns() != null && listTasksResult.getTaskArns().size() > 0) {
          DescribeTasksResult describeTasksResult = amazonECS.describeTasks(
            new DescribeTasksRequest().withCluster(clusterArn).withTasks(listTasksResult.getTaskArns()));

          for (Task task: describeTasksResult.getTasks()) {
            InstanceStatus ec2InstanceStatus = containerInformationService.getEC2InstanceStatus(
              amazonEC2,
              containerInformationService.getContainerInstance(amazonECS, task));

            List<Map<String, String>> healthStatus = containerInformationService.getHealthStatus(clusterArn, task.getTaskArn(), serviceArn, credentials.getName(), "us-west-2");
            instances.add(new EcsTask(extractTaskIdFromTaskArn(task.getTaskArn()), task, ec2InstanceStatus, healthStatus));
          }
        }

        DescribeServicesResult describeServicesResult =
          amazonECS.describeServices(new DescribeServicesRequest().withCluster(clusterArn).withServices(serviceArn));
        ServerGroup.Capacity capacity = new ServerGroup.Capacity();
        capacity.setDesired(describeServicesResult.getServices().get(0).getDesiredCount());
        capacity.setMin(describeServicesResult.getServices().get(0).getDesiredCount());  // TODO - perhaps we want to look at the % min and max for the service?
        capacity.setMax(describeServicesResult.getServices().get(0).getDesiredCount());  // TODO - perhaps we want to look at the % min and max for the service?
        long creationTime = describeServicesResult.getServices().get(0).getCreatedAt().getTime();
        String clusterName = inferClusterNameFromClusterArn(describeServicesResult.getServices().get(0).getClusterArn());

        DescribeTaskDefinitionRequest taskDefinitionRequest = new DescribeTaskDefinitionRequest().withTaskDefinition(describeServicesResult.getServices().get(0).getTaskDefinition());
        DescribeTaskDefinitionResult taskDefinitionResult = amazonECS.describeTaskDefinition(taskDefinitionRequest);
        TaskDefinition taskDefinition = new TaskDefinition();

        com.amazonaws.services.ecs.model.TaskDefinition definition = taskDefinitionResult.getTaskDefinition();
        ContainerDefinition containerDefinition = definition.getContainerDefinitions().get(0);
        String roleArn = describeServicesResult.getServices().get(0).getRoleArn();
        String iamRole = roleArn != null ? roleArn.split("/")[1] : "None";
        taskDefinition
          .setContainerImage(containerDefinition.getImage())
          .setContainerPort(containerDefinition.getPortMappings().get(0).getContainerPort())
          .setCpuUnits(containerDefinition.getCpu())
          .setMemoryReservation(containerDefinition.getMemoryReservation())
          .setIamRole(iamRole)
          .setTaskName(definition.getTaskDefinitionArn().split("/")[1])
        ;


        EcsServerGroup ecsServerGroup = generateServerGroup(awsRegion, metadata, instances, capacity, creationTime, clusterName, taskDefinition);
        EcsServerCluster spinnakerCluster = generateSpinnakerServerCluster(credentials, metadata, loadBalancers, ecsServerGroup);

        if (clusterMap.get(metadata.applicationName) != null) {
          clusterMap.get(metadata.applicationName).add(spinnakerCluster);
        } else {
          clusterMap.put(metadata.applicationName, Sets.newHashSet(spinnakerCluster));
        }
      }
    }

    return clusterMap;
  }

  private Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> extractLoadBalancersData(
    DescribeLoadBalancersResult loadBalancersResult) {
    Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> loadBalancers = Sets.newHashSet();
    for (LoadBalancer elb: loadBalancersResult.getLoadBalancers()) {
      AmazonLoadBalancer loadBalancer = new AmazonLoadBalancer();
      loadBalancer.setName(elb.getLoadBalancerName());
      loadBalancers.add(loadBalancer);
    }
    return loadBalancers;
  }

  private EcsServerCluster generateSpinnakerServerCluster(AmazonCredentials credentials,
                                                          ServiceMetadata metadata,
                                                          Set<com.netflix.spinnaker.clouddriver.model.LoadBalancer> loadBalancers,
                                                          EcsServerGroup ecsServerGroup) {
    return new EcsServerCluster()
          .setAccountName(credentials.getName())
          .setName(metadata.cloudStack)
          .setLoadBalancers(loadBalancers)
          .setServerGroups(Sets.newHashSet(ecsServerGroup));
  }

  private EcsServerGroup generateServerGroup(AmazonCredentials.AWSRegion awsRegion,
                                             ServiceMetadata metadata,
                                             Set<Instance> instances,
                                             ServerGroup.Capacity capacity,
                                             long creationTime,
                                             String ecsCluster,
                                             TaskDefinition taskDefinition) {
    ServerGroup.InstanceCounts instanceCounts = generateInstanceCount(instances);

    return new EcsServerGroup()
      .setName(constructServerGroupName(metadata))
      .setCloudProvider(EcsCloudProvider.ID)
      .setType(EcsCloudProvider.ID)
      .setRegion(awsRegion.getName())
      .setInstances(instances)
      .setCapacity(capacity)
      .setInstanceCounts(instanceCounts)
      .setCreatedTime(creationTime)
      .setEcsCluster(ecsCluster)
      .setTaskDefinition(taskDefinition)
      ;
  }

  private ServerGroup.InstanceCounts generateInstanceCount(Set<Instance> instances) {
    ServerGroup.InstanceCounts instanceCounts = new ServerGroup.InstanceCounts();
    for (Instance instance: instances) {
      switch (instance.getHealthState()) {
        case Up:
          instanceCounts.setUp(instanceCounts.getUp() + 1);
          break;
        case Down:
          instanceCounts.setDown(instanceCounts.getDown() + 1);
          break;
        case Failed:
          instanceCounts.setDown(instanceCounts.getDown() + 1);
          break;
        case Starting:
          instanceCounts.setOutOfService(instanceCounts.getOutOfService() + 1);
          break;
        case Unknown:
          instanceCounts.setUnknown(instanceCounts.getUnknown() + 1);
          break;
        case OutOfService:
          instanceCounts.setOutOfService(instanceCounts.getOutOfService() + 1);
          break;
        case Succeeded:
          instanceCounts.setUp(instanceCounts.getUp());
          break;
        default:
          throw new Error(String.format(
            "Unexpected health state: %s.  Don't know how to proceed - update %s",
            instance.getHealthState(),
            this.getClass().getSimpleName()));
      }
      instanceCounts.setTotal(instanceCounts.getTotal() + 1);
    }
    return instanceCounts;
  }

  private String constructServerGroupName(ServiceMetadata metadata) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(metadata.applicationName).append("-");

    if (metadata.cloudStack != null) {
      stringBuilder.append(metadata.cloudStack).append("-");
    }

    stringBuilder.append(metadata.serverGroupVersion);
    return stringBuilder.toString(); // TODO - support CLOUD_DETAIL variable
  }

  private ServiceMetadata extractMetadataFromServiceArn(String arn) {
    if (!arn.contains("/")) {
      return null; // TODO - do a better verification,
    }

    String[] splitArn = arn.split("/");
    if (splitArn.length != 2) {
      return null; // TODO - do a better verification,
    }

    String[] splitResourceName = splitArn[1].split("-");

    if (splitResourceName.length < 2) {
      return null; // TODO - do a better verification, and handle cases with both cloudStack and CloudDetail
    }

    ServiceMetadata serviceMetadata = new ServiceMetadata();
    String serverGroupVersion = splitResourceName.length > 2 ? splitResourceName[2] : "v0001"; // TODO - implement proper logic here
    serviceMetadata
      .setApplicationName(splitResourceName[0])
      .setCloudStack(splitResourceName[1])
      .setServerGroupVersion(serverGroupVersion);

    return serviceMetadata;
  }

  @Data
  @NoArgsConstructor
  class ServiceMetadata {
    String applicationName;
    String cloudStack;
    String serverGroupVersion;
  }

  private String inferClusterNameFromClusterArn(String clusterArn) {
    return clusterArn.split("/")[1];
  }

  private String extractTaskIdFromTaskArn(String taskArn) {
    return taskArn.split("/")[1];
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public Map<String, Set<EcsServerCluster>> getClusterSummaries(String application) {
    return getClusters();
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public Map<String, Set<EcsServerCluster>> getClusterDetails(String application) {
    Map<String, Set<EcsServerCluster>> result = getClusters();

    Iterator<Map.Entry<String, Set<EcsServerCluster>>> iterator = result.entrySet().iterator();

    while (iterator.hasNext()) {
      Map.Entry<String, Set<EcsServerCluster>> entry = iterator.next();
      if (!entry.getKey().equals(application)) {
        iterator.remove();
      }
    }

    return result;
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public Set<EcsServerCluster> getClusters(String application, String account) {
    return getClusters().get(application);
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name) {
    return getClusters().get(application).iterator().next();
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name, boolean includeDetails) {

    Set<EcsServerCluster> ecsServerClusters = getClusters().get(application);
    if (ecsServerClusters == null || ecsServerClusters.size() == 0) {
      return null;
    } else {
      return ecsServerClusters.iterator().next();
    }
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public ServerGroup getServerGroup(String account, String region, String serverGroupName) {
    // TODO - use a caching system, and also check for account which is currently not the case here
    Map<String, Set<EcsServerCluster>> clusters = getClusters();

    for (Map.Entry<String, Set<EcsServerCluster>> entry: clusters.entrySet()) {
      if (entry.getKey().equals(serverGroupName.split("-")[0])) {
        for (EcsServerCluster ecsServerCluster: entry.getValue()) {
          for (ServerGroup serverGroup: ecsServerCluster.getServerGroups()) {
            if (region.equals(serverGroup.getRegion())
                  && serverGroupName.equals(serverGroup.getName())) {
              return serverGroup;
            }
          }
        }
      }
    }

    throw new Error(String.format("Server group %s not found", serverGroupName));
  }

  @Override
  public String getCloudProviderId() {
    return EcsCloudProvider.ID;
  }

  /**
   Temporary implementation to satisfy the interface's implementation.
   This will be modified and updated properly once we finish the POC
   */
  @Override
  public boolean supportsMinimalClusters() {
    return false;
  }

}
