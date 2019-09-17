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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.*;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationResult;
import com.amazonaws.services.lambda.model.VpcConfig;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionConfigurationDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import org.springframework.util.StringUtils;

public class UpdateLambdaConfigurationAtomicOperation
    extends AbstractLambdaAtomicOperation<
        CreateLambdaFunctionConfigurationDescription, UpdateFunctionConfigurationResult>
    implements AtomicOperation<UpdateFunctionConfigurationResult> {

  public UpdateLambdaConfigurationAtomicOperation(
      CreateLambdaFunctionConfigurationDescription description) {
    super(description, "UPDATE_LAMBDA_FUNCTION_CONFIGURATION");
  }

  @Override
  public UpdateFunctionConfigurationResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Updating of AWS Lambda Function Configuration Operation...");
    return updateFunctionConfigurationResult();
  }

  private UpdateFunctionConfigurationResult updateFunctionConfigurationResult() {
    LambdaFunction cache =
        (LambdaFunction)
            lambdaFunctionProvider.getFunction(
                description.getAccount(), description.getRegion(), description.getFunctionName());

    AWSLambda client = getLambdaClient();
    UpdateFunctionConfigurationRequest request =
        new UpdateFunctionConfigurationRequest()
            .withFunctionName(cache.getFunctionArn())
            .withDescription(description.getDescription())
            .withHandler(description.getHandler())
            .withMemorySize(description.getMemory())
            .withRole(description.getRole())
            .withTimeout(description.getTimeout())
            .withDeadLetterConfig(description.getDeadLetterConfig())
            .withVpcConfig(
                new VpcConfig()
                    .withSecurityGroupIds(description.getSecurityGroupIds())
                    .withSubnetIds(description.getSubnetIds()))
            .withKMSKeyArn(description.getEncryKMSKeyArn())
            .withTracingConfig(description.getTracingConfig());

    UpdateFunctionConfigurationResult result = client.updateFunctionConfiguration(request);
    updateTaskStatus("Finished Updating of AWS Lambda Function Configuration Operation...");
    if (StringUtils.isEmpty(description.getTargetGroup())) {
      if (!cache.getTargetGroups().isEmpty()) {
        AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();
        for (String groupName : cache.getTargetGroups()) {
          deregisterTarget(
              loadBalancingV2,
              cache.getFunctionArn(),
              retrieveTargetGroup(loadBalancingV2, groupName).getTargetGroupArn());
          updateTaskStatus("De-registered the target group...");
        }
      }

    } else {
      AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();
      if (cache.getTargetGroups().isEmpty()) {
        registerTarget(
            loadBalancingV2,
            cache.getFunctionArn(),
            retrieveTargetGroup(loadBalancingV2, description.getTargetGroup()).getTargetGroupArn());
        updateTaskStatus("Registered the target group...");
      } else {
        for (String groupName : cache.getTargetGroups()) {
          if (!groupName.equals(description.getTargetGroup())) {
            registerTarget(
                loadBalancingV2,
                cache.getFunctionArn(),
                retrieveTargetGroup(loadBalancingV2, description.getTargetGroup())
                    .getTargetGroupArn());
            updateTaskStatus("Registered the target group...");
          }
        }
      }
    }
    return result;
  }

  private TargetGroup retrieveTargetGroup(
      AmazonElasticLoadBalancing loadBalancingV2, String targetGroupName) {

    DescribeTargetGroupsRequest request =
        new DescribeTargetGroupsRequest().withNames(targetGroupName);
    DescribeTargetGroupsResult describeTargetGroupsResult =
        loadBalancingV2.describeTargetGroups(request);

    if (describeTargetGroupsResult.getTargetGroups().size() == 1) {
      return describeTargetGroupsResult.getTargetGroups().get(0);
    } else if (describeTargetGroupsResult.getTargetGroups().size() > 1) {
      throw new IllegalArgumentException(
          "There are multiple target groups with the name " + targetGroupName + ".");
    } else {
      throw new IllegalArgumentException(
          "There is no target group with the name " + targetGroupName + ".");
    }
  }

  private AmazonElasticLoadBalancing getAmazonElasticLoadBalancingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonElasticLoadBalancingV2(
        credentialAccount, getRegion(), false);
  }

  private void registerTarget(
      AmazonElasticLoadBalancing loadBalancingV2, String functionArn, String targetGroupArn) {
    RegisterTargetsResult result =
        loadBalancingV2.registerTargets(
            new RegisterTargetsRequest()
                .withTargets(new TargetDescription().withId(functionArn))
                .withTargetGroupArn(targetGroupArn));
  }

  private void deregisterTarget(
      AmazonElasticLoadBalancing loadBalancingV2, String functionArn, String targetGroupArn) {
    DeregisterTargetsResult result =
        loadBalancingV2.deregisterTargets(
            new DeregisterTargetsRequest()
                .withTargetGroupArn(targetGroupArn)
                .withTargets(new TargetDescription().withId(functionArn)));
  }
}
