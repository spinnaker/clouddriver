package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.Service;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.apache.commons.lang3.StringUtils;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

public class ServiceCachingAgent extends AbstractEcsCachingAgent<Service>{
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(SERVICES.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public ServiceCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
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
  protected List<Service> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<Service> serviceList = new LinkedList<>();
    Collection<CacheData> clusters = providerCache.getAll(ECS_CLUSTERS.toString());

    for (CacheData cluster : clusters) {
      String nextToken = null;
      do {
        ListServicesRequest listServicesRequest = new ListServicesRequest().withCluster((String) cluster.getAttributes().get("clusterName"));
        if (nextToken != null) {
          listServicesRequest.setNextToken(nextToken);
        }
        ListServicesResult listServicesResult = ecs.listServices(listServicesRequest);
        List<String> serviceArns = listServicesResult.getServiceArns();
        if (serviceArns.size() == 0) {
          continue;
        }

        List<Service> services = ecs.describeServices(new DescribeServicesRequest().withCluster((String) cluster.getAttributes().get("clusterName")).withServices(serviceArns)).getServices();
        serviceList.addAll(services);

        nextToken = listServicesResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return serviceList;
  }

  @Override
  protected CacheResult buildCacheResult(List<Service> services) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    for (Service service : services) {
      Map<String, Object> attributes = new HashMap<>();
      String applicationName = service.getServiceName().contains("-") ? StringUtils.substringBefore(service.getServiceName(), "-") : service.getServiceName();
      String clusterName = StringUtils.substringAfterLast(service.getClusterArn(), "/");

      attributes.put("applicationName", applicationName);
      attributes.put("serviceName", service.getServiceName());
      attributes.put("serviceArn", service.getServiceArn());
      attributes.put("clusterName", clusterName);
      attributes.put("clusterArn", service.getClusterArn());
      attributes.put("roleArn", service.getRoleArn());
      attributes.put("taskDefinition", service.getTaskDefinition());
      attributes.put("desiredCount", service.getDesiredCount());
      attributes.put("maximumPercent", service.getDeploymentConfiguration().getMaximumPercent());
      attributes.put("minimumHealthyPercent", service.getDeploymentConfiguration().getMinimumHealthyPercent());
      attributes.put("loadBalancers", service.getLoadBalancers());


      String key = Keys.getServiceKey(accountName, region, service.getServiceName());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " services in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(SERVICES.toString(), dataPoints);
    return new DefaultCacheResult(dataMap);
  }

}
