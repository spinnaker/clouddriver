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
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpdateLambdaCodeDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;

;

public class UpdateLambdaCodeAtomicOperation extends AbstractAwsLambdaAtomicOperation<UpdateLambdaCodeDescription, UpdateFunctionCodeResult> implements AtomicOperation<UpdateFunctionCodeResult> {


  public UpdateLambdaCodeAtomicOperation(UpdateLambdaCodeDescription description) {
    super(description, "UPDATE_LAMBDA_FUNCTION_CODE");
  }

  @Override
  public UpdateFunctionCodeResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Code Operation...");
    return updateFunctionConfigurationResult();
  }

  private UpdateFunctionCodeResult updateFunctionConfigurationResult (){
    AwsLambdaCacheModel cache = awsLambdaProvider.getAwsLambdaFunction(description.getProperty("application").toString(),description.getProperty("region").toString(),description.getAccount());


    AWSLambda client = getAwsLambdaClient();
    UpdateFunctionCodeRequest request = new UpdateFunctionCodeRequest()
      .withFunctionName(cache.getFunctionArn())
      .withPublish(Boolean.parseBoolean(description.getProperty("publish").toString()))
      .withS3Bucket(description.getProperty("s3bucket").toString())
      .withS3Key(description.getProperty("s3key").toString());

    UpdateFunctionCodeResult result = client.updateFunctionCode(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Code Operation...");

    return result;
  }




}
