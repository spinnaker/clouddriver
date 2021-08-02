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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;

import com.amazonaws.services.lambda.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
<<<<<<< HEAD
=======
import com.netflix.spinnaker.clouddriver.lambda.service.LambdaService;
import com.netflix.spinnaker.clouddriver.lambda.service.config.LambdaServiceConfig;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
>>>>>>> ba0ce6986 (feat(lambda): run sdk calls concurrently (#5452))
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LambdaCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {
  private static final Set<AgentDataType> types =
      new HashSet<>() {
        {
          add(AUTHORITATIVE.forType(LAMBDA_FUNCTIONS.ns));
        }
      };

  private final NetflixAmazonCredentials account;
  private final String region;
  private OnDemandMetricsSupport metricsSupport;
  private final Registry registry;
<<<<<<< HEAD
=======
  private final Clock clock = Clock.systemDefaultZone();
  private LambdaService lambdaService;
>>>>>>> ba0ce6986 (feat(lambda): run sdk calls concurrently (#5452))

  LambdaCachingAgent(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      LambdaServiceConfig lambdaServiceConfig,
      ServiceLimitConfiguration serviceLimitConfiguration) {
    this.account = account;
    this.region = region;
    this.registry = new DefaultRegistry();
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            AmazonCloudProvider.ID + ":" + AmazonCloudProvider.ID + ":" + OnDemandType.Function);
    this.lambdaService =
        new LambdaService(
            amazonClientProvider,
            account,
            region,
            objectMapper,
            lambdaServiceConfig,
            serviceLimitConfiguration);
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + LambdaCachingAgent.class.getSimpleName();
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

    Map<String, CacheData> lambdaCacheData = new ConcurrentHashMap<>();
    Map<String, Collection<String>> appLambdaRelationships = new ConcurrentHashMap<>();

<<<<<<< HEAD
    String nextMarker = null;
    List<FunctionConfiguration> lstFunction = new ArrayList<FunctionConfiguration>();

    do {
      ListFunctionsRequest listFunctionsRequest = new ListFunctionsRequest();
      if (nextMarker != null) {
        listFunctionsRequest.setMarker(nextMarker);
      }

      ListFunctionsResult listFunctionsResult = lambda.listFunctions(listFunctionsRequest);

      lstFunction.addAll(listFunctionsResult.getFunctions());
      nextMarker = listFunctionsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);

    Collection<CacheData> data = new LinkedList<>();
    Collection<CacheData> appData = new LinkedList<>();
    Map<String, Collection<String>> appRelationships = new HashMap<String, Collection<String>>();

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
    for (FunctionConfiguration x : lstFunction) {
      Map<String, Object> attributes = objectMapper.convertValue(x, ATTRIBUTES);
      attributes.put("account", account.getName());
      attributes.put("region", region);
      attributes.put("revisions", listFunctionRevisions(x.getFunctionArn()));
      List<AliasConfiguration> allAliases = listAliasConfiguration(x.getFunctionArn());
      attributes.put("aliasConfigurations", allAliases);
      List<EventSourceMappingConfiguration> eventSourceMappings =
          listEventSourceMappingConfiguration(x.getFunctionArn());
      List<EventSourceMappingConfiguration> aliasEvents = new ArrayList<>();
      for (AliasConfiguration currAlias : allAliases) {
        List<EventSourceMappingConfiguration> currAliasEvents =
            listEventSourceMappingConfiguration(currAlias.getAliasArn());
        aliasEvents.addAll(currAliasEvents);
      }
      eventSourceMappings.addAll(aliasEvents);
      attributes.put("eventSourceMappings", eventSourceMappings);

      attributes = addConfigAttributes(attributes, x, lambda);
      String functionName = x.getFunctionName();
      attributes.put("targetGroups", getTargetGroupNames(lambda, functionName));
      Names names = Names.parseName(functionName);
      if (null != names.getApp()) {
        String appKey =
            com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(names.getApp());
        Collection<String> functionKeys = appRelationships.get(appKey);
        String functionKey = Keys.getLambdaFunctionKey(account.getName(), region, functionName);

        if (null == functionKeys) {
          functionKeys = new ArrayList<>();
          appRelationships.put(appKey, functionKeys);
        }
        functionKeys.add(functionKey);
      }
      data.add(
          new DefaultCacheData(
              Keys.getLambdaFunctionKey(account.getName(), region, x.getFunctionName()),
              attributes,
              Collections.emptyMap()));
    }
    for (String appKey : appRelationships.keySet()) {
      appData.add(
          new DefaultCacheData(
              appKey,
              Collections.emptyMap(),
              Collections.singletonMap(LAMBDA_FUNCTIONS.ns, appRelationships.get(appKey))));
    }
    cacheResults.put(LAMBDA_FUNCTIONS.ns, data);
    cacheResults.put(
        com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS.ns, appData);
    log.info("Caching {} items in {}", String.valueOf(data.size()), getAgentType());
    return new DefaultCacheResult(cacheResults);
  }

  private Map<String, String> listFunctionRevisions(String functionArn) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    Map<String, String> listRevionIds = new HashMap<String, String>();
    do {
      ListVersionsByFunctionRequest listVersionsByFunctionRequest =
          new ListVersionsByFunctionRequest();
      listVersionsByFunctionRequest.setFunctionName(functionArn);
      if (nextMarker != null) {
        listVersionsByFunctionRequest.setMarker(nextMarker);
      }

      ListVersionsByFunctionResult listVersionsByFunctionResult =
          lambda.listVersionsByFunction(listVersionsByFunctionRequest);
      for (FunctionConfiguration x : listVersionsByFunctionResult.getVersions()) {
        listRevionIds.put(x.getRevisionId(), x.getVersion());
      }
      nextMarker = listVersionsByFunctionResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return listRevionIds;
  }

  private List<AliasConfiguration> listAliasConfiguration(String functionArn) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<AliasConfiguration> aliasConfigurations = new ArrayList<>();
    do {
      ListAliasesRequest listAliasesRequest = new ListAliasesRequest();
      listAliasesRequest.setFunctionName(functionArn);
      if (nextMarker != null) {
        listAliasesRequest.setMarker(nextMarker);
      }

      ListAliasesResult listAliasesResult = lambda.listAliases(listAliasesRequest);
      for (AliasConfiguration x : listAliasesResult.getAliases()) {
        aliasConfigurations.add(x);
      }
      nextMarker = listAliasesResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return aliasConfigurations;
  }

  private final List<EventSourceMappingConfiguration> listEventSourceMappingConfiguration(
      String functionArn) {
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = new ArrayList<>();

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    do {
      ListEventSourceMappingsRequest listEventSourceMappingsRequest =
          new ListEventSourceMappingsRequest();
      listEventSourceMappingsRequest.setFunctionName(functionArn);

      if (nextMarker != null) {
        listEventSourceMappingsRequest.setMarker(nextMarker);
      }

      ListEventSourceMappingsResult listEventSourceMappingsResult =
          lambda.listEventSourceMappings(listEventSourceMappingsRequest);

      for (EventSourceMappingConfiguration x :
          listEventSourceMappingsResult.getEventSourceMappings()) {
        eventSourceMappingConfigurations.add(x);
      }
      nextMarker = listEventSourceMappingsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);

    return eventSourceMappingConfigurations;
=======
    // Get All Lambda's
    List<Map<String, Object>> allLambdas;
    try {
      allLambdas = lambdaService.getAllFunctions();
    } catch (Exception e) {
      throw new SpinnakerException(
          "Failed to populate the lambda cache for account '"
              + account.getName()
              + "' and region '"
              + region
              + "' because: "
              + e.getMessage());
    }

    buildCacheData(lambdaCacheData, appLambdaRelationships, allLambdas);

    Collection<CacheData> processedOnDemandCache = new ArrayList<>();

    // Process on demand cache
    Collection<CacheData> onDemandCacheData =
        providerCache
            .getAll(
                ON_DEMAND.getNs(),
                providerCache.filterIdentifiers(
                    ON_DEMAND.getNs(),
                    Keys.getLambdaFunctionKey(getAccountName(), getRegion(), "*")))
            .stream()
            .filter(d -> (int) d.getAttributes().get("processedCount") == 0)
            .collect(Collectors.toList());

    for (CacheData onDemandItem : onDemandCacheData) {
      try {
        long cachedAt = (long) onDemandItem.getAttributes().get("cacheTime");
        if (cachedAt > loadDataStart) {
          CacheData currentLambda = lambdaCacheData.get(onDemandItem.getId());
          if (currentLambda != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            LocalDateTime onDemandLastModified =
                LocalDateTime.parse(
                    (String) onDemandItem.getAttributes().get("lastModified"), formatter);
            LocalDateTime currentLambdaLastModified =
                LocalDateTime.parse(
                    (String) currentLambda.getAttributes().get("lastModified"), formatter);
            if (onDemandLastModified.isAfter(currentLambdaLastModified)) {
              lambdaCacheData.put(onDemandItem.getId(), onDemandItem);
              String appKey =
                  onDemandItem.getRelationships().get(APPLICATIONS.ns).stream().findFirst().get();
              Collection<String> functionkeys =
                  appLambdaRelationships.getOrDefault(appKey, new ArrayList<>());
              functionkeys.add(onDemandItem.getId());
              appLambdaRelationships.put(appKey, functionkeys);
            }
          } else {
            lambdaCacheData.put(onDemandItem.getId(), onDemandItem);
            String appKey =
                onDemandItem.getRelationships().get(APPLICATIONS.ns).stream().findFirst().get();
            Collection<String> functionkeys =
                appLambdaRelationships.getOrDefault(appKey, new ArrayList<>());
            functionkeys.add(onDemandItem.getId());
            appLambdaRelationships.put(appKey, functionkeys);
          }
        }
        Map<String, Object> attr = onDemandItem.getAttributes();
        attr.put("processedCount", 1);
        processedOnDemandCache.add(
            new DefaultCacheData(onDemandItem.getId(), attr, Collections.emptyMap()));
      } catch (Exception e) {
        log.warn("Failed to process onDemandCache for Lambda's: " + e.getMessage());
      }
    }

    // Create the INFORMATIVE spinnaker application cache with lambda relationships
    Collection<CacheData> appCacheData = new LinkedList<>();
    for (String appKey : appLambdaRelationships.keySet()) {
      appCacheData.add(
          new DefaultCacheData(
              appKey,
              Collections.emptyMap(),
              Collections.singletonMap(LAMBDA_FUNCTIONS.ns, appLambdaRelationships.get(appKey))));
    }

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();

    cacheResults.put(LAMBDA_FUNCTIONS.ns, lambdaCacheData.values());
    cacheResults.put(APPLICATIONS.ns, appCacheData);
    cacheResults.put(ON_DEMAND.ns, processedOnDemandCache);

    Map<String, Collection<String>> evictions =
        computeEvictableData(lambdaCacheData.values(), providerCache);

    log.info("Caching {} items in {}", String.valueOf(lambdaCacheData.size()), getAgentType());
    return new DefaultCacheResult(cacheResults, evictions);
  }

  private void buildCacheData(
      Map<String, CacheData> lambdaCacheData,
      Map<String, Collection<String>> appLambdaRelationships,
      List<Map<String, Object>> allLambdas) {
    allLambdas.parallelStream()
        .forEach(
            lf -> {
              String functionName = (String) lf.get("functionName");
              String functionKey =
                  Keys.getLambdaFunctionKey(getAccountName(), getRegion(), functionName);

              /* TODO: If the functionName follows frigga by chance (i.e. somename-someothername), it will try to store the
                 lambda as a relationship with the app name (somename), even if it wasn't deployed by spinnaker!
              */
              // Add the spinnaker application relationship and store it
              Names names = Names.parseName(functionName);
              if (names.getApp() != null) {
                String appKey =
                    com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(
                        names.getApp());
                appLambdaRelationships.compute(
                    appKey,
                    (k, v) -> {
                      Collection<String> fKeys = v;
                      if (fKeys == null) fKeys = new ArrayList<>();
                      fKeys.add(functionKey);
                      return fKeys;
                    });
                // No other thread should be putting the same function in this map. Its safe to use
                // put
                lambdaCacheData.put(
                    functionKey,
                    new DefaultCacheData(
                        functionKey,
                        lf,
                        Collections.singletonMap(
                            APPLICATIONS.ns, Collections.singletonList(appKey))));
              } else {
                // TODO: Do we care about non spinnaker deployed lambdas?
                lambdaCacheData.put(
                    functionKey, new DefaultCacheData(functionKey, lf, Collections.emptyMap()));
              }
            });
>>>>>>> ba0ce6986 (feat(lambda): run sdk calls concurrently (#5452))
  }

  private final Map<String, Object> addConfigAttributes(
      Map<String, Object> attributes, FunctionConfiguration x, AWSLambda lambda) {
    GetFunctionRequest getFunctionRequest = new GetFunctionRequest();
    getFunctionRequest.setFunctionName(x.getFunctionArn());
    GetFunctionResult getFunctionResult = lambda.getFunction(getFunctionRequest);
    attributes.put("vpcConfig", getFunctionResult.getConfiguration().getVpcConfig());
    attributes.put("code", getFunctionResult.getCode());
    attributes.put("tags", getFunctionResult.getTags());
    attributes.put("concurrency", getFunctionResult.getConcurrency());
    attributes.put("state", getFunctionResult.getConfiguration().getState());
    attributes.put("stateReason", getFunctionResult.getConfiguration().getStateReason());
    attributes.put("stateReasonCode", getFunctionResult.getConfiguration().getStateReasonCode());
    return attributes;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.Function) && cloudProvider.equals(AmazonCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!validKeys(data)
        || !data.get("account").equals(getAccountName())
        || !data.get("region").equals(region)) {
      return null;
    }

    String appName = (String) data.get("appName");
    String functionName = combineAppDetail(appName, (String) data.get("functionName"));

    String functionKey =
        Keys.getLambdaFunctionKey(
            (String) data.get("credentials"), (String) data.get("region"), functionName);

    String appKey = com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(appName);

<<<<<<< HEAD
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);

    GetFunctionResult functionResult = null;
    try {
      functionResult = lambda.getFunction(new GetFunctionRequest().withFunctionName(functionName));
    } catch (ResourceNotFoundException ex) {
      log.info("Function {} Not exist", functionName);
    }

    Map<String, Object> attributes = new HashMap<String, Object>();
    attributes.put("name", appName);
    Map<String, Collection<String>> evictions = Collections.emptyMap();
=======
    Map<String, Object> lambdaAttributes = null;
    try {
      lambdaAttributes = lambdaService.getFunctionByName(functionName);
    } catch (Exception e) {
      if (e instanceof ResourceNotFoundException) {
        // do nothing, the lambda was deleted
      } else {
        throw new SpinnakerException(
            "Failed to populate the onDemandCache for lambda '" + functionName + "'");
      }
    }
>>>>>>> ba0ce6986 (feat(lambda): run sdk calls concurrently (#5452))

    Collection<String> existingFunctionRel = null;

<<<<<<< HEAD
    CacheData application = providerCache.get(APPLICATIONS.ns, appKey);
=======
    if (lambdaAttributes != null && !lambdaAttributes.isEmpty()) {
      lambdaAttributes.put("cacheTime", clock.instant().toEpochMilli());
      lambdaAttributes.put("processedCount", 0);
      DefaultCacheData lambdaCacheData =
          new DefaultCacheData(
              functionKey,
              lambdaAttributes,
              Collections.singletonMap(APPLICATIONS.ns, Collections.singletonList(appKey)));
>>>>>>> ba0ce6986 (feat(lambda): run sdk calls concurrently (#5452))

    if (null != application && null != application.getRelationships()) {
      existingFunctionRel = application.getRelationships().get(LAMBDA_FUNCTIONS.ns);
    }

    Map<String, Collection<String>> relationships = new HashMap<String, Collection<String>>();

    if (null != existingFunctionRel && !existingFunctionRel.isEmpty()) {
      if (null == functionResult && existingFunctionRel.contains(functionKey)) {
        existingFunctionRel.remove(functionKey);
        evictions.put(LAMBDA_FUNCTIONS.ns, Collections.singletonList(functionKey));
      } else {
        existingFunctionRel.add(functionKey);
      }

    } else {
      existingFunctionRel = Collections.singletonList(functionKey);
    }

    relationships.put(LAMBDA_FUNCTIONS.ns, existingFunctionRel);
    DefaultCacheData cacheData = new DefaultCacheData(appKey, attributes, relationships);
    DefaultCacheResult defaultCacheresults =
        new DefaultCacheResult(
            Collections.singletonMap(
                com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS.ns,
                Collections.singletonList(cacheData)));

    return new OnDemandAgent.OnDemandResult(getAgentType(), defaultCacheresults, evictions);
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    return null;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  private Boolean validKeys(Map<String, ? extends Object> data) {
    return (data.containsKey("functionName")
        && data.containsKey("credentials")
        && data.containsKey("region"));
  }

  protected String combineAppDetail(String appName, String functionName) {
    Names functionAppName = Names.parseName(functionName);
    if (null != functionAppName) {
      return functionAppName.getApp().equals(appName)
          ? functionName
          : (appName + "-" + functionName);
    } else {
      throw new IllegalArgumentException(
          String.format("Function name {%s} contains invlaid charachetrs ", functionName));
    }
  }

<<<<<<< HEAD
  private List<String> getTargetGroupNames(AWSLambda lambda, String functionName) {
    List<String> targetGroupNames = new ArrayList<>();
    Predicate<Statement> isAllowStatement =
        statement -> statement.getEffect().toString().equals(Statement.Effect.Allow.toString());
    Predicate<Statement> isLambdaInvokeAction =
        statement ->
            statement.getActions().stream()
                .anyMatch(action -> action.getActionName().equals("lambda:InvokeFunction"));
    Predicate<Statement> isElbPrincipal =
        statement ->
            statement.getPrincipals().stream()
                .anyMatch(
                    principal -> principal.getId().equals("elasticloadbalancing.amazonaws.com"));

    try {
      GetPolicyResult result =
          lambda.getPolicy(new GetPolicyRequest().withFunctionName(functionName));
      String json = result.getPolicy();
      Policy policy = Policy.fromJson(json);

      targetGroupNames =
          policy.getStatements().stream()
              .filter(isAllowStatement.and(isLambdaInvokeAction).and(isElbPrincipal))
              .flatMap(statement -> statement.getConditions().stream())
              .filter(
                  condition ->
                      condition.getType().equals("ArnLike")
                          && condition.getConditionKey().equals("AWS:SourceArn"))
              .flatMap(condition -> condition.getValues().stream())
              .filter(value -> ArnUtils.extractTargetGroupName(value).isPresent())
              .map(name -> ArnUtils.extractTargetGroupName(name).get())
              .collect(Collectors.toList());

    } catch (ResourceNotFoundException ex) {
      // ignore the exception.
      log.info("No policies exist for {}", functionName);
    }

    return targetGroupNames;
=======
  /**
   * Provides the key namespace that the caching agent is authoritative of. Currently only supports
   * the caching agent being authoritative over one key namespace. Taken from
   * AbstractEcsCachingAgent
   *
   * @return Key namespace.
   */
  String getAuthoritativeKeyName() {
    Collection<AgentDataType> authoritativeNamespaces =
        getProvidedDataTypes().stream()
            .filter(agentDataType -> agentDataType.getAuthority().equals(AUTHORITATIVE))
            .collect(Collectors.toSet());

    if (authoritativeNamespaces.size() != 1) {
      throw new RuntimeException(
          "LambdaCachingAgent supports only one authoritative key namespace. "
              + authoritativeNamespaces.size()
              + " authoritative key namespace were given.");
    }

    return authoritativeNamespaces.iterator().next().getTypeName();
  }

  Map<String, Collection<String>> computeEvictableData(
      Collection<CacheData> newData, ProviderCache providerCache) {

    // Get all old keys from the cache for the region and account
    String authoritativeKeyName = getAuthoritativeKeyName();
    Set<String> oldKeys =
        providerCache.getIdentifiers(authoritativeKeyName).stream()
            .filter(
                key -> {
                  Map<String, String> keyParts = Keys.parse(key);
                  return keyParts.get("account").equalsIgnoreCase(account.getName())
                      && keyParts.get("region").equalsIgnoreCase(region);
                })
            .collect(Collectors.toSet());

    // New data can only come from the current account and region, no need to filter.
    Set<String> newKeys = newData.stream().map(CacheData::getId).collect(Collectors.toSet());

    Set<String> evictedKeys =
        oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(getAuthoritativeKeyName(), evictedKeys);
    String prettyKeyName =
        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, getAuthoritativeKeyName());

    log.info(
        "Evicting "
            + evictedKeys.size()
            + " "
            + prettyKeyName
            + (evictedKeys.size() > 1 ? "s" : "")
            + " in "
            + getAgentType());

    return evictionsByKey;
>>>>>>> ba0ce6986 (feat(lambda): run sdk calls concurrently (#5452))
  }
}
