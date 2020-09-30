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

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.model.*;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.UpdateLambdaEdgeConfigurationDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class UpdateLambdaEdgeConfigurationAtomicOperation
    extends AbstractLambdaAtomicOperation<UpdateLambdaEdgeConfigurationDescription, Object>
    implements AtomicOperation<Object> {

  public UpdateLambdaEdgeConfigurationAtomicOperation(
      UpdateLambdaEdgeConfigurationDescription description) {
    super(description, "UPDATE_LAMBDA_FUNCTION_EDGE_CONFIGURATION");
  }

  @Override
  public Object operate(List priorOutputs) {
    String functionName = description.getFunctionName();
    String region = description.getRegion();
    String account = description.getAccount();

    LambdaFunction cache =
        (LambdaFunction) lambdaFunctionProvider.getFunction(account, region, functionName);
    return updateCloudFrontDistribution(cache);
  }

  private UpdateDistributionResult updateCloudFrontDistribution(LambdaFunction cache) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Edge Configuration");
    AmazonCloudFront client = getCloudfrontClient();

    LambdaFunctionAssociations lfiList = new LambdaFunctionAssociations();
    LambdaFunctionAssociation lfItem = new LambdaFunctionAssociation();
    lfItem.setEventType(EventType.valueOf(description.getEventType()));
    lfItem.setLambdaFunctionARN(cache.getFunctionArn());
    lfItem.setIncludeBody(description.isIncludeBody());
    lfiList.setQuantity(1);
    lfiList.setItems(List.of(lfItem));

    // First get the existing distribution  and its default cache behavior
    GetDistributionRequest req = new GetDistributionRequest();
    req.setId(description.getDistributionId());
    GetDistributionResult gdr = client.getDistribution(req);
    DistributionConfig currDistributionConfig = gdr.getDistribution().getDistributionConfig();
    DefaultCacheBehavior currDefaultCacheBehavior =
        currDistributionConfig.getDefaultCacheBehavior();

    // Update the lambda association of the existing default cache behavior
    currDefaultCacheBehavior.setLambdaFunctionAssociations(lfiList);

    // Send a new request to effect this change at AWS.
    UpdateDistributionRequest request = new UpdateDistributionRequest();
    request.setDistributionConfig(currDistributionConfig);
    request.setId(description.getDistributionId());
    // Not clear what this does.
    request.setIfMatch(gdr.getETag());

    UpdateDistributionResult result = client.updateDistribution(request); // Exception thrown here.
    updateTaskStatus("Finished Updating of AWS Lambda Function Edge Configuration");
    return result;
  }
}
