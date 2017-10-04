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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EnableServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class EnableServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENABLE_ECS_SERVER_GROUP";

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  ContainerInformationService containerInformationService;

  EnableServiceDescription description;

  public EnableServiceAtomicOperation(EnableServiceDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Enable Amazon ECS Server Group Operation...");
    enableService();
    return null;
  }

  private void enableService() {
    AmazonECS ecsClient = getAmazonEcsClient();

    String service = description.getServerGroupName();
    String account = description.getCredentialAccount();
    String cluster = getCluster(service, account);

    UpdateServiceRequest request = new UpdateServiceRequest()
      .withCluster(cluster)
      .withService(service)
      .withDesiredCount(getMaxCapacity());

    updateTaskStatus(String.format("Enabling %s service for %s.", service, account));
    ecsClient.updateService(request);
    updateTaskStatus(String.format("Service %s enabled for %s.", service, account));
  }

  private String getCluster(String service, String account) {
    return containerInformationService.getClusterName(service, account, description.getRegion());
  }

  private Integer getMaxCapacity() {
    ScalableTarget target = getScalableTarget();
    if (target != null) {
      return target.getMaxCapacity();
    }
    return 1;
  }

  private ScalableTarget getScalableTarget() {
    AWSApplicationAutoScaling appASClient = getAmazonApplicationAutoScalingClient();

    String service = description.getServerGroupName();
    String account = description.getCredentialAccount();
    String cluster = getCluster(service, account);

    List<String> resourceIds = new ArrayList<>();
    resourceIds.add(String.format("service/%s/%s", cluster, service));

    DescribeScalableTargetsRequest request = new DescribeScalableTargetsRequest()
      .withResourceIds(resourceIds)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withServiceNamespace(ServiceNamespace.Ecs);

    DescribeScalableTargetsResult result = appASClient.describeScalableTargets(request);

    if (result.getScalableTargets().isEmpty()) {
      return null;
    }

    if (result.getScalableTargets().size() == 1) {
      return result.getScalableTargets().get(0);
    }

    throw new Error("Multiple Scalable Targets found");
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = description.getRegion();
    String credentialAccount = description.getCredentialAccount();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, credentialsProvider, region);
  }

  private AmazonECS getAmazonEcsClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = description.getRegion();
    String credentialAccount = description.getCredentialAccount();

    return amazonClientProvider.getAmazonEcs(credentialAccount, credentialsProvider, region);
  }

  private AmazonCredentials getCredentials() {
    return (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
  }

  private void updateTaskStatus(String status) {
    getTask().updateStatus(BASE_PHASE, status);
  }
}
