package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public class ContainerInstanceCachingAgent extends AbstractEcsCachingAgent<ContainerInstance> implements CachingAgent, OnDemandAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(CONTAINER_INSTANCES.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public ContainerInstanceCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider, registry);
  }

  @Override
  public String getAgentType() {
    return ServiceCachingAgent.class.getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  protected List<ContainerInstance> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<ContainerInstance> containerInstanceList = new LinkedList<>();
    Collection<CacheData> clusters = providerCache.getAll(ECS_CLUSTERS.toString());

    for (CacheData cluster : clusters) {
      String nextToken = null;
      do {
        ListContainerInstancesRequest listContainerInstancesRequest = new ListContainerInstancesRequest().withCluster((String) cluster.getAttributes().get("clusterName"));
        if (nextToken != null) {
          listContainerInstancesRequest.setNextToken(nextToken);
        }

        ListContainerInstancesResult listContainerInstancesResult = ecs.listContainerInstances(listContainerInstancesRequest);
        List<String> containerInstanceArns = listContainerInstancesResult.getContainerInstanceArns();
        if (containerInstanceArns.size() == 0) {
          continue;
        }

        List<ContainerInstance> containerInstances = ecs.describeContainerInstances(new DescribeContainerInstancesRequest()
          .withCluster((String) cluster.getAttributes().get("clusterName")).withContainerInstances(containerInstanceArns)).getContainerInstances();
        containerInstanceList.addAll(containerInstances);

        nextToken = listContainerInstancesResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return containerInstanceList;
  }

  @Override
  protected CacheResult buildCacheResult(List<ContainerInstance> containerInstances) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    for (ContainerInstance containerInstance : containerInstances) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("containerInstanceArn", containerInstance.getContainerInstanceArn());
      attributes.put("ec2InstanceId", containerInstance.getEc2InstanceId());

      String key = Keys.getContainerInstanceKey(accountName, region, containerInstance.getContainerInstanceArn());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " container instances in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(CONTAINER_INSTANCES.toString(), dataPoints);
    return new DefaultCacheResult(dataMap);
  }
}
