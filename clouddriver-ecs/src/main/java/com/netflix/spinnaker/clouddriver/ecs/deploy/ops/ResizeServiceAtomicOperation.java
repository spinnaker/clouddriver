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
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ResizeServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_ECS_SERVER_GROUP";
  private static final String ecsClusterName = "poc";  // TODO - get the cluster name from the ContainerInformationService, instead of hard-coding

  private final ResizeServiceDescription description;

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  AmazonClientProvider amazonClientProvider;

  public ResizeServiceAtomicOperation(ResizeServiceDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Resize ECS Server Group Operation...");

    resizeService();
    resizeAutoScalingGroup();

    return null;
  }

  private void resizeService() {
    AmazonECS amazonECS = getAmazonEcsClient();

    String serviceName = description.getServerGroupName();
    Integer desiredCount = description.getCapacity().getDesired();

    UpdateServiceRequest updateServiceRequest = new UpdateServiceRequest()
      .withCluster(ecsClusterName)
      .withService(serviceName)
      .withDesiredCount(desiredCount);
    updateTaskStatus(String.format("Resizing %s to %s instances.", serviceName, desiredCount));
    amazonECS.updateService(updateServiceRequest);
    updateTaskStatus(String.format("Done resizing %s to %s", serviceName , desiredCount));
  }

  private void resizeAutoScalingGroup() {
    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();

    String  serviceName = description.getServerGroupName();
    Integer desiredCount = description.getCapacity().getDesired();

    RegisterScalableTargetRequest request = new RegisterScalableTargetRequest()
      .withServiceNamespace(ServiceNamespace.Ecs)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withResourceId(String.format("service/%s/%s", ecsClusterName, serviceName))
      .withMinCapacity(0)
      .withMaxCapacity(desiredCount);

    updateTaskStatus(String.format("Resizing Scalable Target of %s to %s instances", serviceName, desiredCount));
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus(String.format("Done resizing Scalable Target of %s to %s instances", serviceName, desiredCount));
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
