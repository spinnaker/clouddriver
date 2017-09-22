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

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

// TODO: DisableServiceAtomicOperation should not be resizing the service to 0 tasks. It should do something such as removing the instance from the target group.
public class DisableServiceAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DISABLE_ECS_SERVER_GROUP";
  // TODO: Remove hardcoded CLUSTER_NAME.
  private static final String CLUSTER_NAME = "poc";

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  DisableServiceDescription description;

  public DisableServiceAtomicOperation(DisableServiceDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Disable Amazon ECS Server Group Operation...");

    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());

    getTask().updateStatus(BASE_PHASE, "Disabling " + description.getServerGroupName() + " service for " + description.getCredentialAccount() + ".");
    ecs.updateService(new UpdateServiceRequest().withCluster(CLUSTER_NAME).withService(description.getServerGroupName()).withDesiredCount(0));
    getTask().updateStatus(BASE_PHASE, "Service " + description.getServerGroupName() + " disabled for " + description.getCredentialAccount() + ".");

    return null;
  }

}
