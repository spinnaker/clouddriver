package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

public class TaskCachingAgent implements CachingAgent {

  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(TASKS.toString())
  ));

  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String region;
  private String accountName;

  public TaskCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.emptyList();
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);

    Collection<CacheData> dataPoints = new LinkedList<>();
    Collection<CacheData> clusters = providerCache.getAll(ECS_CLUSTERS.toString());

    for (CacheData cluster : clusters) {
      String nextToken = null;
      do {
        ListTasksRequest listTasksRequest = new ListTasksRequest().withCluster((String) cluster.getAttributes().get("clusterName"));
        if (nextToken != null) {
          listTasksRequest.setNextToken(nextToken);
        }
        ListTasksResult listTasksResult = ecs.listTasks(listTasksRequest);
        List<String> taskArns = listTasksResult.getTaskArns();
        List<Task> tasks = ecs.describeTasks(new DescribeTasksRequest().withCluster((String) cluster.getAttributes().get("clusterName")).withTasks(taskArns)).getTasks();

        for (Task task : tasks) {
          Map<String, Object> attributes = new HashMap<>();
          attributes.put("taskArn", task.getTaskArn());
          attributes.put("clusterArn", task.getClusterArn());
          attributes.put("containerInstanceArn", task.getContainerInstanceArn());
          attributes.put("containers", task.getContainers());

          String key = Keys.getTaskKey(accountName, region, task.getContainerInstanceArn());
          dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
        }
        nextToken = listTasksResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASKS.toString(), dataPoints);

    return new DefaultCacheResult(dataMap);
  }

  @Override
  public String getAgentType() {
    return TaskCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }
}
