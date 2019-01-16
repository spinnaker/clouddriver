/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DeployCloudFormationAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "DEPLOY_CLOUDFORMATION";


  @Autowired
  AmazonClientProvider amazonClientProvider;

  @Autowired
  private ObjectMapper objectMapper;

  private DeployCloudFormationDescription description;

  public DeployCloudFormationAtomicOperation(DeployCloudFormationDescription deployCloudFormationDescription) {
    this.description = deployCloudFormationDescription;
  }

  @Override
  public Map operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(BASE_PHASE, "Configurting cloudformation");
    AmazonCloudFormation amazonCloudFormation = amazonClientProvider.getAmazonCloudFormation(
        description.getCredentials(), description.getRegion());
    task.updateStatus(BASE_PHASE, "Preparing cloudformation");
    CreateStackRequest createStackRequest = new CreateStackRequest()
        .withStackName(description.getStackName())
        .withParameters(description.getParameters().entrySet().stream()
            .map(entry -> new Parameter()
                .withParameterKey(entry.getKey())
                .withParameterValue(entry.getValue()))
            .collect(Collectors.toList()));
    try {
      task.updateStatus(BASE_PHASE, "Generating cloudformation");
      createStackRequest = createStackRequest.withTemplateBody(
          objectMapper.writeValueAsString(description.getTemplateBody()));
    } catch (JsonProcessingException e) {
      log.warn("Could not serialize templateBody: {}", description, e);
    }
    task.updateStatus(BASE_PHASE, "Uploading cloudformation");
    CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);
    return Collections.singletonMap("stackId", createStackResult.getStackId());
  }

}
