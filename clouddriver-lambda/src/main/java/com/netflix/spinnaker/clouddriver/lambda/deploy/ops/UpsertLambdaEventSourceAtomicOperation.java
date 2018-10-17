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
import com.amazonaws.services.lambda.model.*;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaEventMappingDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpsertLambdaEventSourceAtomicOperation extends AbstractAwsLambdaAtomicOperation<UpsertLambdaEventMappingDescription, Object> implements AtomicOperation<Object> {


  public UpsertLambdaEventSourceAtomicOperation(UpsertLambdaEventMappingDescription description) {
    super(description, "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING");
  }

  @Override
  public Object operate(List priorOutputs) {

    AwsLambdaCacheModel cache = awsLambdaProvider.getAwsLambdaFunction(description.getProperty("application").toString(),description.getProperty("region").toString(),description.getAccount());
    boolean flagexists = false;

    List<EventSourceMappingConfiguration> eventmappings = cache.getEventSourceMappingConfigurationList();
    for (Object x : eventmappings){
      /*
      anshrma@amazon.com : This is not ideal, but intentional. Ideally, we know that x is not POJO, but AliasConfiguration. However,
      if we iterate though AliasConfiguration instead, we get an exception java.util.LinkedHashMap cannot be cast to com.amazonaws.services.lambda.model.AliasConfiguration.
      Hence this workaround
      */

      HashMap<String,String> eventmap = (HashMap<String, String>) x;
      if (eventmap.get("eventSourceArn").toLowerCase().equals(description.getProperty("eventsourcearn").toString().toLowerCase())) {
        flagexists = true;
        description.setProperty("uuid",eventmap.get("uuid"));
      }

    }


    if (flagexists==true){
      return updateEventSourceMappingResult(cache);
    }
    else{
      return createEventSourceMapping(cache);
    }

  }


  private UpdateEventSourceMappingResult updateEventSourceMappingResult (AwsLambdaCacheModel cache){
    updateTaskStatus("Initializing Updating of AWS Lambda Function Event Mapping Operation...");

    AWSLambda client = getAwsLambdaClient();
    UpdateEventSourceMappingRequest request = new UpdateEventSourceMappingRequest()
      .withFunctionName(cache.getFunctionArn())
      .withBatchSize(Integer.parseInt(description.getProperty("batchsize").toString()))
      .withEnabled(Boolean.parseBoolean(description.getProperty("enabled").toString()))
      .withUUID(description.getProperty("uuid").toString());

    UpdateEventSourceMappingResult result = client.updateEventSourceMapping(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Event Mapping Operation...");

    return result;
  }

  private CreateEventSourceMappingResult createEventSourceMapping (AwsLambdaCacheModel cache){
    updateTaskStatus("Initializing Creation of AWS Lambda Function Event Source Mapping...");

    AWSLambda client = getAwsLambdaClient();
    CreateEventSourceMappingRequest request = new CreateEventSourceMappingRequest()
      .withFunctionName(cache.getFunctionArn())
      .withBatchSize(Integer.parseInt(description.getProperty("batchsize").toString()))
      .withEnabled(Boolean.parseBoolean(description.getProperty("enabled").toString()))
      .withStartingPosition("LATEST")
      .withEventSourceArn(description.getProperty("eventsourcearn").toString());

    CreateEventSourceMappingResult result = client.createEventSourceMapping(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Event Mapping Operation...");

    return result;
  }

}
