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

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.lambda.cache.client.AwsLambdaCacheClient;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;

import java.util.Collection;

@Component
public class AwsLambdaProvider {

  private AwsLambdaCacheClient awslambdaCacheClient;

  @Autowired
  public AwsLambdaProvider(Cache cacheView) {
    this.awslambdaCacheClient = new AwsLambdaCacheClient(cacheView);
  }

  public Collection<AwsLambdaCacheModel> getAllAwsLambdaFunctions() {
    return awslambdaCacheClient.getAll();
  }

  public Collection<AwsLambdaCacheModel> getAllAwsLambdaFunctions(String accountname, String region) {
    return awslambdaCacheClient.getAll(accountname, region);
  }

  public AwsLambdaCacheModel getAwsLambdaFunction(String name, String region, String accountName) {
    String key = Keys.getLambdaFunctionKey(accountName, region, name);
    return awslambdaCacheClient.get(key);
  }

}
