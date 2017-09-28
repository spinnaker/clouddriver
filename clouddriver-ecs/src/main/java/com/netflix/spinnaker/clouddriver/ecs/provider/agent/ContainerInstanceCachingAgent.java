package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public class ContainerInstanceCachingAgent implements CachingAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(CONTAINER_INSTANCES.toString())
  ));

  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String region;
  private String accountName;

  public ContainerInstanceCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);

    Collection<CacheData> dataPoints = new LinkedList<>();
    Collection<CacheData> clusters = providerCache.getAll(ECS_CLUSTERS.toString());

    for (CacheData cluster : clusters) {
      String nextToken = null;
      do {
        ListContainerInstancesRequest listContainerInstancesRequest = new ListContainerInstancesRequest().withCluster((String) cluster.getAttributes().get("name"));
        if (nextToken != null) {
          listContainerInstancesRequest.setNextToken(nextToken);
        }

        ListContainerInstancesResult listContainerInstancesResult = ecs.listContainerInstances(listContainerInstancesRequest);
        List<String> containerInstanceArns = listContainerInstancesResult.getContainerInstanceArns();

        List<ContainerInstance> containerInstances = ecs.describeContainerInstances(new DescribeContainerInstancesRequest()
          .withCluster((String) cluster.getAttributes().get("name")).withContainerInstances(containerInstanceArns)).getContainerInstances();

        for (ContainerInstance containerInstance : containerInstances) {
          Map<String, Object> attributes = new HashMap<>();
          attributes.put("containerInstanceArn", containerInstance.getContainerInstanceArn());
          attributes.put("ec2InstanceId", containerInstance.getEc2InstanceId());

          String key = Keys.getContainerInstanceKey(accountName, region, containerInstance.getContainerInstanceArn());
          dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
        }

        nextToken = listContainerInstancesResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }

    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(CONTAINER_INSTANCES.toString(), dataPoints);

    return new DefaultCacheResult(dataMap);
  }

  @Override
  public String getAgentType() {
    return ContainerInstanceCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }
}
