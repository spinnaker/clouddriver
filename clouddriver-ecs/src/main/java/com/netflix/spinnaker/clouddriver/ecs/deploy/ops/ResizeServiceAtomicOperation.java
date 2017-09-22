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
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.amazonaws.services.ecs.model.UpdateServiceResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
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
    getTask().updateStatus(BASE_PHASE, "Initializing Resize ECS Server Group Operation...");

    String serverGroupName = description.getServerGroupName();
    Integer newSize = description.getCapacity().getDesired();

    getTask().updateStatus(BASE_PHASE, String.format("Resizing %s to %s instances.", serverGroupName, newSize));
    resizeService(serverGroupName, description.getRegion(), newSize);
    getTask().updateStatus(BASE_PHASE, String.format("Done resizing %s to %s", serverGroupName , newSize));
    return null;
  }

  private void resizeService(String serviceName, String region, Integer desiredCount) {
    AmazonCredentials credentials
      = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(credentials.getName(), credentials.getCredentialsProvider(), region);

    UpdateServiceRequest updateServiceRequest = new UpdateServiceRequest()
      .withCluster(ecsClusterName)
      .withService(serviceName)
      .withDesiredCount(desiredCount);

    amazonECS.updateService(updateServiceRequest);
  }
}
