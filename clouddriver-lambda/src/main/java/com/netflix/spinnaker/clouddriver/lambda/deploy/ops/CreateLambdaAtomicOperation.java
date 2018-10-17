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
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.CreateFunctionResult;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.LogType;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.HashMap;
import java.util.stream.*;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CreateLambdaAtomicOperation extends AbstractAwsLambdaAtomicOperation<CreateLambdaDescription, CreateFunctionResult> implements AtomicOperation<CreateFunctionResult> {


  public CreateLambdaAtomicOperation(CreateLambdaDescription description) {
    super(description, "CREATE_LAMBDA_FUNCTION");
  }

  @Override
  public CreateFunctionResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Creation of AWS Lambda Function Operation...");
    return createFunction();
  }


  private CreateFunctionResult createFunction (){
    FunctionCode code = new FunctionCode()
      .withS3Bucket(description.getProperty("s3bucket").toString())
      .withS3Key(description.getProperty("s3key").toString());

    Map<String,String> objTag = new HashMap<String,String>();
    List<Map<String,String>>  lstTag = (List<Map<String, String>>) description.getProperty("tags");
    for (Map<String,String> x: lstTag) {
      for (Entry<String,String> entry : x.entrySet()){
        objTag.put(entry.getKey().toString(),entry.getValue().toString());
      }
    }


    AWSLambda client = getAwsLambdaClient();
    CreateFunctionRequest request = new CreateFunctionRequest();
    request.setFunctionName(description.getProperty("application").toString());
    request.setDescription(description.getProperty("description").toString());
    request.setHandler(description.getProperty("handler").toString());
    request.setMemorySize(Integer.parseInt(description.getProperty("memory").toString()));
    request.setPublish(Boolean.parseBoolean(description.getProperty("publish").toString()));
    request.setRole(description.getProperty("role").toString());
    request.setRuntime(description.getProperty("runtime").toString());
    request.setTimeout(Integer.parseInt(description.getProperty("timeout").toString()));
    request.setCode(code);
    request.setTags(objTag);

    CreateFunctionResult result = client.createFunction(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Operation...");

    return result;
  }

}
