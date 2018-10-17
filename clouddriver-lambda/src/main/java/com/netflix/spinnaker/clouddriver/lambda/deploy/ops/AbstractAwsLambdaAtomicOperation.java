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
import com.amazonaws.services.lambda.AWSLambda;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.AbstractAwsLambdaDescription;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.AwsLambdaProvider;
import com.netflix.spinnaker.clouddriver.lambda.services.AwsLambdaFunctionInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAwsLambdaAtomicOperation<T extends AbstractAwsLambdaDescription, K> implements AtomicOperation<K> {
  private final String basePhase;
  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  AwsLambdaProvider awsLambdaProvider;
  T description;

  AbstractAwsLambdaAtomicOperation(T description, String basePhase) {
    this.description = description;
    this.basePhase = basePhase;

  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

//  String getCluster(String service, String account) {
//    String region = getRegion();
//    return containerInformationService.getClusterName(service, account, region);
//  }

  AWSLambda getAwsLambdaClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();
    return amazonClientProvider.getAmazonLambda(credentialAccount,region);
  }

  protected String getRegion() {
    return description.getRegion();
  }

  AmazonCredentials getCredentials() {
    return (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
  }

  void updateTaskStatus(String status) {
    getTask().updateStatus(basePhase, status);
  }
}
