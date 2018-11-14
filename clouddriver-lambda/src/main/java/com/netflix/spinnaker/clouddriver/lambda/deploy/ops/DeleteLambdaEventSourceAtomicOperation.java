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
import java.util.List;

public class DeleteLambdaEventSourceAtomicOperation extends AbstractAwsLambdaAtomicOperation<UpsertLambdaEventMappingDescription, Object> implements AtomicOperation<Object> {


  public DeleteLambdaEventSourceAtomicOperation(UpsertLambdaEventMappingDescription description) {
    super(description, "DELETE_LAMBDA_FUNCTION_EVENT_MAPPING");
  }

  @Override
  public Object operate(List priorOutputs) {
    String application = description.getProperty("application").toString();
    String region = description.getProperty("region").toString();
    String account = description.getAccount();
    AwsLambdaCacheModel cache = awsLambdaProvider.getAwsLambdaFunction(application, region, account);
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
      return deleteEventSourceMappingResult(cache);
    }
    else{
      return null;
    }

  }


  private DeleteEventSourceMappingResult deleteEventSourceMappingResult (AwsLambdaCacheModel cache){
    updateTaskStatus("Initializing Deleting of AWS Lambda Function Event Mapping Operation...");

    AWSLambda client = getAwsLambdaClient();
    DeleteEventSourceMappingRequest request = new DeleteEventSourceMappingRequest()
      .withUUID(description.getProperty("uuid").toString());

    DeleteEventSourceMappingResult result = client.deleteEventSourceMapping(request);
    updateTaskStatus("Finished Deleting of AWS Lambda Function Event Mapping Operation...");

    return result;
  }

}
