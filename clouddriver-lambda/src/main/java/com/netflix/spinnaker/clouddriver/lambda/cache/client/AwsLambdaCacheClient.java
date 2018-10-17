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

package com.netflix.spinnaker.clouddriver.lambda.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_NAME;

@Component
public class AwsLambdaCacheClient extends AbstractCacheClient<AwsLambdaCacheModel>{

  @Autowired
  public AwsLambdaCacheClient(Cache cacheView) {
    super(cacheView, LAMBDA_NAME.toString());
  }

  @Override
  protected AwsLambdaCacheModel convert(CacheData cacheData) {
    AwsLambdaCacheModel awsLambdaCacheModel = new AwsLambdaCacheModel();
    Map<String, Object> attributes = cacheData.getAttributes();

    awsLambdaCacheModel.setFunctionName(attributes.get("functionname").toString());
    awsLambdaCacheModel.setFunctionArn(attributes.get("functionarn").toString());
    awsLambdaCacheModel.setAccountName(attributes.get("accountname").toString());
    awsLambdaCacheModel.setRegion(attributes.get("region").toString());
    awsLambdaCacheModel.setHandler(attributes.get("handler").toString());
    awsLambdaCacheModel.setDescription(attributes.get("description").toString());
    awsLambdaCacheModel.setLastModified(attributes.get("lastmodified").toString());
    awsLambdaCacheModel.setRevisionId(attributes.get("revisionid").toString());
    awsLambdaCacheModel.setRuntime(attributes.get("runtime").toString());
    awsLambdaCacheModel.setrevisionidmap(attributes.get("revisionids"));
    awsLambdaCacheModel.setAliasConfigurations(attributes.get("aliasconfiguration"));
    awsLambdaCacheModel.setEventSourceMappingConfigurationList(attributes.get("eventsourcemappings"));

    return awsLambdaCacheModel;
  }
}
