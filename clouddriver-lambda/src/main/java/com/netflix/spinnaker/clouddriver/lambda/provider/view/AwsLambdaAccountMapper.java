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

package com.netflix.spinnaker.clouddriver.lambda.provider.view;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.security.NetflixAssumeRoleAwsLambdaCredentials;
import com.netflix.spinnaker.clouddriver.lambda.security.NetflixAwsLambdaCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AwsLambdaAccountMapper {

  final AccountCredentialsProvider accountCredentialsProvider;
  final Map<String, NetflixAssumeRoleAwsLambdaCredentials> awslambdaCredentialsMap;
  final Map<String, NetflixAmazonCredentials> awsCredentialsMap;

  @Autowired
  public AwsLambdaAccountMapper(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;

    Set<? extends AccountCredentials> allAccounts = accountCredentialsProvider.getAll();

    Collection<NetflixAssumeRoleAwsLambdaCredentials> awslambdaAccounts =
      (Collection<NetflixAssumeRoleAwsLambdaCredentials>) allAccounts
        .stream()
        .filter(credentials -> credentials instanceof NetflixAssumeRoleAwsLambdaCredentials)
        .collect(Collectors.toSet());

    awslambdaCredentialsMap = new HashMap<>();
    awsCredentialsMap = new HashMap<>();

    for (NetflixAssumeRoleAwsLambdaCredentials awslambdaAccount : awslambdaAccounts) {
      awslambdaCredentialsMap.put(awslambdaAccount.getAwsAccount(), awslambdaAccount);

      allAccounts
        .stream()
        .filter(credentials -> credentials.getName().equals(awslambdaAccount.getAwsAccount()))
        .findFirst()
        .ifPresent(v -> awsCredentialsMap.put(awslambdaAccount.getName(), (NetflixAmazonCredentials) v));
    }
  }

  public NetflixAwsLambdaCredentials fromAwsAccountNameToAwsLambda(String awsAccoutName) {
    return awslambdaCredentialsMap.get(awsAccoutName);
  }

  public NetflixAmazonCredentials fromAwsLambdaAccountNameToAws(String awslambdaAccountName) {
    return awsCredentialsMap.get(awslambdaAccountName);
  }
}
