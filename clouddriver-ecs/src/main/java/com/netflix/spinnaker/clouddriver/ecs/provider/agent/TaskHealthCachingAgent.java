package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.core.provider.agent.HealthProvidingCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

public class TaskHealthCachingAgent implements CachingAgent, HealthProvidingCachingAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(HEALTH.toString())
  ));
  private final static String HEALTH_ID = "ecs-task-instance-health";
  private final Logger log = LoggerFactory.getLogger(getClass());
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String region;
  private String accountName;

  public TaskHealthCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);
    AmazonElasticLoadBalancing amazonloadBalancing = amazonClientProvider.getAmazonElasticLoadBalancingV2(accountName, awsCredentialsProvider, region);

    Collection<CacheData> dataPoints = new LinkedList<>();
    Collection<String> taskEvicitions = new LinkedList<>();

    Collection<CacheData> tasksCache = providerCache.getAll(TASKS.toString());
    if(tasksCache != null ) {
      for (CacheData taskCache : tasksCache) {
        String containerInstanceCacheKey = Keys.getContainerInstanceKey(accountName, region, (String) taskCache.getAttributes().get("containerInstanceArn"));
        CacheData containerInstance = providerCache.get(CONTAINER_INSTANCES.toString(), containerInstanceCacheKey);

        String serviceName = StringUtils.substringAfter((String) taskCache.getAttributes().get("group"), "service:");
        String serviceCacheKey = Keys.getServiceKey(accountName, region, serviceName);
        CacheData serviceCache = providerCache.get(SERVICES.toString(), serviceCacheKey);
        if (serviceCache == null || containerInstance == null) {
          taskEvicitions.add(taskCache.getId());
          continue;
        }

        int port = 0;
        try {
          port = (Integer) ((List<Map<String, Object>>) ((List<Map<String, Object>>) taskCache.getAttributes().get("containers")).get(0).get("networkBindings")).get(0).get("hostPort");
        } catch (NullPointerException e) {
          e.printStackTrace();
        }

        List<Map<String, Object>> loadBalancers = new LinkedList<>();
        try {
          loadBalancers = (List<Map<String, Object>>) serviceCache.getAttributes().get("loadBalancers");
        } catch (NullPointerException e) {
          e.printStackTrace();
        }
        for (Map<String, Object> loadBalancer : loadBalancers) {
          DescribeTargetHealthResult describeTargetHealthResult = amazonloadBalancing.describeTargetHealth(
            new DescribeTargetHealthRequest().withTargetGroupArn((String) loadBalancer.get("targetGroupArn")).withTargets(
              new TargetDescription().withId((String) containerInstance.getAttributes().get("ec2InstanceId")).withPort(port)));

          Map<String, Object> attributes = new HashMap<>();
          attributes.put("instanceId", (String) taskCache.getAttributes().get("taskArn"));

          String targetHealth = describeTargetHealthResult.getTargetHealthDescriptions().get(0).getTargetHealth().getState();

          attributes.put("state", targetHealth.equals("healthy") ? "Up" : "Unknown");  // TODO - Return better values, and think of a better strategy at defining health
          attributes.put("type", "loadBalancer");

          String key = Keys.getTaskHealthKey(accountName, region, (String) taskCache.getAttributes().get("taskId"));
          dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
        }
      }
    }

    log.info("Caching " + dataPoints.size() + " task health checks in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(HEALTH.toString(), dataPoints);

    log.info("Evicting " + taskEvicitions.size() + " task health checks in " + getAgentType());
    Map<String, Collection<String>> evictionMap = new HashMap<>();
    evictionMap.put(TASKS.toString(), taskEvicitions);

    return new DefaultCacheResult(dataMap, evictionMap);
  }

  @Override
  public String getAgentType() {
    return TaskHealthCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public String getHealthId() {
    return HEALTH_ID;
  }
}
