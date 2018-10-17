/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaConfigurationDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import java.util.List;

;

public class UpdateLambdaConfigurationAtomicOperation extends AbstractAwsLambdaAtomicOperation<CreateLambdaConfigurationDescription, UpdateFunctionConfigurationResult> implements AtomicOperation<UpdateFunctionConfigurationResult> {


  public UpdateLambdaConfigurationAtomicOperation(CreateLambdaConfigurationDescription description) {
    super(description, "UPDATE_LAMBDA_FUNCTION_CONFIGURATION");
  }

  @Override
  public UpdateFunctionConfigurationResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Configuration Operation...");
    return updateFunctionConfigurationResult();
  }

  private UpdateFunctionConfigurationResult updateFunctionConfigurationResult (){
    AwsLambdaCacheModel cache = awsLambdaProvider.getAwsLambdaFunction(description.getProperty("application").toString(),description.getProperty("region").toString(),description.getAccount());


    AWSLambda client = getAwsLambdaClient();
    UpdateFunctionConfigurationRequest request = new UpdateFunctionConfigurationRequest()
      .withFunctionName(cache.getFunctionArn())
      .withDescription(description.getProperty("description").toString())
      .withHandler(description.getProperty("handler").toString())
      .withMemorySize(Integer.parseInt(description.getProperty("memory").toString()))
      .withRole(description.getProperty("role").toString())
      .withTimeout(Integer.parseInt(description.getProperty("timeout").toString()));

    UpdateFunctionConfigurationResult result = client.updateFunctionConfiguration(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Configuration Operation...");
    return result;
  }




}
