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

package com.netflix.spinnaker.clouddriver.lambda.provider.agent;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.AliasConfiguration;
import com.amazonaws.services.lambda.model.EventSourceMappingConfiguration;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.ListAliasesRequest;
import com.amazonaws.services.lambda.model.ListAliasesResult;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsRequest;
import com.amazonaws.services.lambda.model.ListEventSourceMappingsResult;
import com.amazonaws.services.lambda.model.ListFunctionsRequest;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_NAME;

@Slf4j
public class AmazonLambdaFunctionCachingAgent implements CachingAgent, AccountAware {

  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final String region;
  private static final Set<AgentDataType> types = Collections.unmodifiableSet(DefaultGroovyMethods.asType(new ArrayList<AgentDataType>(Arrays.asList(AUTHORITATIVE.forType(LAMBDA_NAME.ns))), Set.class));

  public AmazonLambdaFunctionCachingAgent(AmazonClientProvider amazonClientProvider, NetflixAmazonCredentials account, String region) {
    this.amazonClientProvider = amazonClientProvider;
    this.account = account;
    this.region = region;
  }

  @Override
  public String getProviderName() {
    return AwsInfrastructureProvider.PROVIDER_NAME;
  }

  @Override
  public String getAgentType() {
    return getAccount().getName() + "/" + getRegion() + "/" + AmazonLambdaFunctionCachingAgent.class.getSimpleName();
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in {}", getAgentType());

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<FunctionConfiguration> lstFunction = new ArrayList<FunctionConfiguration>();

    do {
      ListFunctionsRequest listFunctionsRequest = new ListFunctionsRequest();
      if (nextMarker != null) {
        listFunctionsRequest.setMarker(nextMarker);
      }

      ListFunctionsResult listFunctionsResult  = lambda.listFunctions(listFunctionsRequest);
      lstFunction.addAll(listFunctionsResult.getFunctions());
      nextMarker = listFunctionsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);

    Collection<CacheData> data = new LinkedList<>();
    for (FunctionConfiguration x : lstFunction) {

      Map<String, Object> attributes = new HashMap<>();
      attributes.put("functionname", x.getFunctionName());
      attributes.put("functionarn", x.getFunctionArn());
      attributes.put("accountname", account.getName());
      attributes.put("region", region);
      attributes.put("handler", x.getHandler());
      attributes.put("description",x.getDescription());
      attributes.put("lastmodified",x.getLastModified());
      attributes.put("revisionid",x.getRevisionId());
      attributes.put("runtime",x.getRuntime());
      attributes.put("revisionids",listFunctionRevisions(x.getFunctionArn()));
      attributes.put("aliasconfiguration",listAliasConfiguration(x.getFunctionArn()));
      attributes.put("eventsourcemappings",listEventSourceMappingConfiguration(x.getFunctionArn()));

      DefaultCacheData item = new DefaultCacheData(Keys.getLambdaFunctionKey(account.getName(),region,x.getFunctionName()),attributes,Collections.emptyMap());
      data.add(item);
    }

    log.info("Caching {} items in {}", String.valueOf(data.size()), getAgentType());

    Map<String, Collection<CacheData>> map =new HashMap<>();
    map.put(LAMBDA_NAME.ns, data);
    return new DefaultCacheResult(map);

  }

  private Map<String,String> listFunctionRevisions(String FunctionArn){

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    Map<String,String> listRevionIds = new HashMap<String,String>();
    do {
      ListVersionsByFunctionRequest listVersionsByFunctionRequest = new ListVersionsByFunctionRequest();
      listVersionsByFunctionRequest.setFunctionName(FunctionArn);
      if (nextMarker != null) {
        listVersionsByFunctionRequest.setMarker(nextMarker);
      }

      ListVersionsByFunctionResult listVersionsByFunctionResult  = lambda.listVersionsByFunction(listVersionsByFunctionRequest);
      for (FunctionConfiguration x : listVersionsByFunctionResult.getVersions()){
        listRevionIds.put(x.getRevisionId(),x.getVersion());
      }
      nextMarker = listVersionsByFunctionResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return listRevionIds;
  }

  private List<AliasConfiguration> listAliasConfiguration (String FunctionArn){
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<AliasConfiguration> aliasConfigurations = new ArrayList<>();
    do {
      ListAliasesRequest listAliasesRequest = new ListAliasesRequest();
      listAliasesRequest.setFunctionName(FunctionArn);
      if (nextMarker != null) {
        listAliasesRequest.setMarker(nextMarker);
      }

      ListAliasesResult listAliasesResult  = lambda.listAliases(listAliasesRequest);
      for (AliasConfiguration x:listAliasesResult.getAliases()){
        aliasConfigurations.add(x);
      }
      nextMarker = listAliasesResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return aliasConfigurations;

  }

  public final List<EventSourceMappingConfiguration> listEventSourceMappingConfiguration(String FunctionArn){
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = new ArrayList<>();
    do {
      ListEventSourceMappingsRequest listEventSourceMappingsRequest = new ListEventSourceMappingsRequest();
      listEventSourceMappingsRequest.setFunctionName(FunctionArn);
      if (nextMarker != null) {
        listEventSourceMappingsRequest.setMarker(nextMarker);
      }

      ListEventSourceMappingsResult listEventSourceMappingsResult  = lambda.listEventSourceMappings(listEventSourceMappingsRequest);
      for (EventSourceMappingConfiguration x :listEventSourceMappingsResult.getEventSourceMappings()){
        eventSourceMappingConfigurations.add(x);
      }
      nextMarker = listEventSourceMappingsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return eventSourceMappingConfigurations;

  }
  public final AmazonClientProvider getAmazonClientProvider() {
    return amazonClientProvider;
  }

  public final NetflixAmazonCredentials getAccount() {
    return account;
  }

  public final String getRegion() {
    return region;
  }

  public static Set<AgentDataType> getTypes() {
    return types;
  }

}
