/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancerAttributeResponse;
import com.aliyuncs.slb.model.v20140515.DescribeLoadBalancersResponse.LoadBalancer;
import com.aliyuncs.slb.model.v20140515.DescribeVServerGroupsResponse.VServerGroup;
import com.google.common.collect.Lists;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.common.Sets;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.alicloud.provider.agent.AliCloudLoadBalancerCachingAgent.LoadBalancerCacheBuilder;
import com.netflix.spinnaker.clouddriver.alicloud.provider.view.AliCloudLoadBalancerProvider.ResultDetails;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

public class AliCloudLoadBalancerProviderTest extends CommonProvider {
  private String applicationName = "test-application";
  private String clusterKey;
  private String serverGroupKey;
  LoadBalancer loadBalancer = fromMockData("mock/loadbalancer.json", LoadBalancer.class);

  @Before
  public void testBefore() {
    String scalingGroupName = "test-application-fromspinnaker-test-v003";
    Names name = Names.parseName(scalingGroupName);
    applicationName = name.getApp();
    clusterKey = Keys.getClusterKey(name.getCluster(), name.getApp(), ACCOUNT);
    serverGroupKey = Keys.getServerGroupKey(scalingGroupName, ACCOUNT, REGION);

    when(cacheView.get(eq(APPLICATIONS.ns), eq(Keys.getApplicationKey(applicationName))))
        .thenAnswer(
            (Answer<CacheData>)
                invocation -> {
                  Map<String, Object> attributes = new HashMap<>();
                  attributes.put("name", applicationName);

                  Map<String, Collection<String>> relationships = new HashMap<>(16);
                  relationships.put(CLUSTERS.ns, Sets.ofModifiable(clusterKey));
                  relationships.put(SERVER_GROUPS.ns, Sets.ofModifiable(serverGroupKey));
                  relationships.put(LOAD_BALANCERS.ns, Sets.ofModifiable(serverGroupKey));
                  return new DefaultCacheData(
                      Keys.getApplicationKey(applicationName), attributes, relationships);
                });

    when(cacheView.getAll(eq(LOAD_BALANCERS.ns), anyCollection()))
        .thenAnswer(
            (Answer<Collection<CacheData>>)
                invocation -> {
                  DescribeLoadBalancerAttributeResponse loadBalancerAttribute =
                      fromMockData(
                          "mock/describeLoadBalancerAttributeResponse.json",
                          DescribeLoadBalancerAttributeResponse.class);
                  loadBalancerAttribute.setLoadBalancerName(loadBalancer.getLoadBalancerName());
                  Map listenerAttribute = fromMockData("mock/listenerAttributes.json", Map.class);
                  VServerGroup vServerGroup =
                      fromMockData("mock/vserverGroup.json", VServerGroup.class);
                  return Lists.newArrayList(
                      new LoadBalancerCacheBuilder(
                              loadBalancer,
                              loadBalancerAttribute,
                              Lists.newArrayList(listenerAttribute),
                              Lists.newArrayList(vServerGroup),
                              ACCOUNT,
                              REGION)
                          .buildLoadBalancerCache(objectMapper));
                });

    when(cacheView.getAll(eq(SERVER_GROUPS.ns), anyCollection()))
        .thenAnswer((Answer<Collection<CacheData>>) invocation -> Collections.EMPTY_LIST);
  }

  @Test
  public void testGetApplicationLoadBalancers() {

    AliCloudLoadBalancerProvider provider =
        new AliCloudLoadBalancerProvider(objectMapper, cacheView, oldProvider);

    Set<AliCloudLoadBalancer> applicationLoadBalancers =
        provider.getApplicationLoadBalancers(applicationName);
    assertEquals(1, applicationLoadBalancers.size());
    AliCloudLoadBalancer aliCloudLoadBalancer = applicationLoadBalancers.iterator().next();
    assertEquals(aliCloudLoadBalancer.getLoadBalancerId(), loadBalancer.getLoadBalancerId());
    assertEquals(aliCloudLoadBalancer.getAccount(), ACCOUNT);
    assertEquals(aliCloudLoadBalancer.getRegion(), loadBalancer.getRegionIdAlias());
    assertEquals(aliCloudLoadBalancer.getVpcId(), loadBalancer.getVpcId());
  }

  @Test
  public void testByAccountAndRegionAndName() {
    String loadBalancer = "lbName";
    AliCloudLoadBalancerProvider provider =
        new AliCloudLoadBalancerProvider(objectMapper, cacheView, oldProvider);

    when(cacheView.filterIdentifiers(eq(LOAD_BALANCERS.ns), anyString()))
        .thenReturn(Lists.newArrayList("test-key"));

    List<ResultDetails> lbName = provider.byAccountAndRegionAndName(ACCOUNT, REGION, loadBalancer);

    assertEquals(1, lbName.size());
  }
}
