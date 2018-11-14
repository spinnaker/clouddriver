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

package com.netflix.spinnaker.clouddriver.lambda.cache.model;

import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.EventSourceMappingConfiguration;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AwsLambdaCacheModel extends FunctionConfiguration{
  private String accountName;
  private String region;
  private Map<String,String> revisionids ;
  private List<AliasConfiguration> aliasConfigurations;
  private List<EventSourceMappingConfiguration> eventSourceMappingConfigurationList;

  public AwsLambdaCacheModel withAccountName(String accountName){
    setAccountName(accountName);
    return this;
  }

  public AwsLambdaCacheModel withRegion(String region){
    setRegion(region);
    return this;
  }

  public void setAccountName(String accountName){
    this.accountName=accountName;
  }

  public void setRegion(String region){
    this.region=region;
  }


  public void setrevisionidmap(Object revisionids) {
    this.revisionids= (Map<String, String>) revisionids;
  }

  public void setAliasConfigurations (Object listaliasesresult){
    this.aliasConfigurations = (List<AliasConfiguration>) listaliasesresult;
  }

  public List<AliasConfiguration> getAliasConfigurations(){
    return this.aliasConfigurations;
  }

  public void setEventSourceMappingConfigurationList (Object eventSourceMappingConfiguration){
    this.eventSourceMappingConfigurationList = (List<EventSourceMappingConfiguration>) eventSourceMappingConfiguration;
  }

  public List<EventSourceMappingConfiguration> getEventSourceMappingConfigurationList(){
    return this.eventSourceMappingConfigurationList;
  }
}
