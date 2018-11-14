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
import com.amazonaws.services.lambda.model.AliasRoutingConfiguration;
import com.amazonaws.services.lambda.model.CreateAliasRequest;
import com.amazonaws.services.lambda.model.CreateAliasResult;
import com.amazonaws.services.lambda.model.UpdateAliasRequest;
import com.amazonaws.services.lambda.model.UpdateAliasResult;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpsertLambdaAliasDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.amazonaws.services.lambda.model.AliasConfiguration;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public class UpsertLambdaAliasAtomicOperation extends AbstractAwsLambdaAtomicOperation<UpsertLambdaAliasDescription, Object> implements AtomicOperation<Object> {


  public UpsertLambdaAliasAtomicOperation(UpsertLambdaAliasDescription description) {
    super(description, "UPSERT_LAMBDA_FUNCTION_ALIAS");
  }

  @Override
  public Object operate(List priorOutputs) {

    AwsLambdaCacheModel cache = awsLambdaProvider.getAwsLambdaFunction(description.getProperty("application").toString(),description.getProperty("region").toString(),description.getAccount());
    boolean flagexists = false;

    List<AliasConfiguration> aliases = cache.getAliasConfigurations();

    for (Object x : aliases){
      /*
      anshrma@amazon.com : This is not ideal, but intentional. Ideally, we know that x is not POJO, but AliasConfiguration. However,
      if we iterate though AliasConfiguration instead, we get an exception java.util.LinkedHashMap cannot be cast to com.amazonaws.services.lambda.model.AliasConfiguration.
      Hence this workaround
      */

      HashMap<String,String> aliasmap = (HashMap<String, String>) x;
      if (aliasmap.get("name").toLowerCase().equals(description.getProperty("aliasname").toString().toLowerCase())) {
        flagexists = true;
      }

    }


    if (flagexists==true){
      return updateAliasResult(cache);
    }
    else{
      return createAliasResult(cache);
    }

  }


  private UpdateAliasResult updateAliasResult (AwsLambdaCacheModel cache){
    updateTaskStatus("Initializing Updating of AWS Lambda Function Alias Operation...");

    Map<String,Double> routingconfig = new LinkedHashMap<>();
    String minorFunctionVersion = description.getProperty("minorfunctionversion").toString();
    String weightToMinorFunctionVersion = description.getProperty("weighttominorfunctionversion").toString();

    if (StringUtils.isNotEmpty(minorFunctionVersion) && StringUtils.isNotEmpty(weightToMinorFunctionVersion)) {
      routingconfig.put(description.getProperty("minorfunctionversion").toString(),Double.parseDouble(description.getProperty("weighttominorfunctionversion").toString()));
    }

    AWSLambda client = getAwsLambdaClient();
    UpdateAliasRequest request = new UpdateAliasRequest()
      .withFunctionName(cache.getFunctionArn())
      .withDescription(description.getProperty("aliasdescription").toString())
      .withFunctionVersion(description.getProperty("majorfunctionversion").toString())
      .withName(description.getProperty("aliasname").toString())
      .withRoutingConfig(new AliasRoutingConfiguration()
        .withAdditionalVersionWeights(routingconfig));

    UpdateAliasResult result = client.updateAlias(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Alias Operation...");

    return result;
  }

  private CreateAliasResult createAliasResult (AwsLambdaCacheModel cache){
    updateTaskStatus("Initializing Creation of AWS Lambda Function Alias Operation...");
    Map<String,Double> routingconfig = new LinkedHashMap<>();
    if (description.getProperty("minorfunctionversion") !="" && description.getProperty("weighttominorfunctionversion")!=""){
      routingconfig.put(description.getProperty("minorfunctionversion").toString(),Double.parseDouble(description.getProperty("weighttominorfunctionversion").toString()));
    }

    AWSLambda client = getAwsLambdaClient();
    CreateAliasRequest request = new CreateAliasRequest()
      .withFunctionName(cache.getFunctionArn())
      .withDescription(description.getProperty("aliasdescription").toString())
      .withFunctionVersion(description.getProperty("majorfunctionversion").toString())
      .withName(description.getProperty("aliasname").toString())
      .withRoutingConfig(new AliasRoutingConfiguration()
      .withAdditionalVersionWeights(routingconfig));

    CreateAliasResult result = client.createAlias(request);
    updateTaskStatus("Finished Creation of AWS Lambda Function Alias Operation...");

    return result;
  }

}
