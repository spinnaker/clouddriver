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
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

// TODO: DisableServiceAtomicOperation should not be resizing the service to 0 tasks. It should do something such as removing the instance from the target group.
public class DisableServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DISABLE_ECS_SERVER_GROUP";

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  ContainerInformationService containerInformationService;

  DisableServiceDescription description;

  public DisableServiceAtomicOperation(DisableServiceDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Disable Amazon ECS Server Group Operation...");
    disableService();
    return null;
  }

  private void disableService() {
    AmazonECS ecs = getAmazonEcsClient();

    String service = description.getServerGroupName();
    String account = description.getCredentialAccount();
    String cluster = getCluster(service, account);

    updateTaskStatus(String.format("Disabling %s service for %s.", service, account));
    UpdateServiceRequest request = new UpdateServiceRequest()
      .withCluster(cluster)
      .withService(service)
      .withDesiredCount(0);
    ecs.updateService(request);
    updateTaskStatus(String.format("Service %s disabled for %s.", service, account));
  }

  private String getCluster(String service, String account) {
    String region = description.getRegion();
    return containerInformationService.getClusterName(service, account, region);
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
