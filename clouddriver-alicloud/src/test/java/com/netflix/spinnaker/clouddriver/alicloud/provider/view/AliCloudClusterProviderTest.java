/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudCluster;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudServerGroup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudClusterProviderTest extends CommonProvider {

  @Before
  public void testBefore() {}

  @Test
  public void testGetClusterDetails() {
    when(cacheView.get(anyString(), anyString()))
        .thenAnswer(new ApplicationAnswer())
        .thenAnswer(new ClustersAnswer())
        .thenAnswer(new ServerGroupsAnswer());
    AliCloudClusterProvider provider =
        new AliCloudClusterProvider(objectMapper, cacheView, new AliCloudProvider());
    Map<String, Set<AliCloudCluster>> clusterDetails =
        provider.getClusterDetails("test-application");
    assertTrue(clusterDetails.get("test-application").size() == 1);
  }

  @Test
  public void testGetCluster() {
    when(cacheView.get(anyString(), anyString()))
        .thenAnswer(new ClustersAnswer())
        .thenAnswer(new ServerGroupsAnswer());
    AliCloudClusterProvider provider =
        new AliCloudClusterProvider(objectMapper, cacheView, new AliCloudProvider());
    AliCloudCluster cluster =
        provider.getCluster("test-application", ACCOUNT, "test-cluster", true);
    assertTrue(cluster != null);
  }

  @Test
  public void testGetServerGroup() {
    when(cacheView.get(anyString(), anyString())).thenAnswer(new ServerGroupsAnswer());
    AliCloudClusterProvider provider =
        new AliCloudClusterProvider(objectMapper, cacheView, new AliCloudProvider());
    AliCloudServerGroup serverGroup =
        provider.getServerGroup(ACCOUNT, REGION, "test-cluster", true);
    assertTrue(serverGroup != null);
  }

  private class ServerGroupsAnswer implements Answer<CacheData> {
    @Override
    public CacheData answer(InvocationOnMock invocation) throws Throwable {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("account", ACCOUNT);
      attributes.put("name", "test-serverGroupName");
      attributes.put("region", REGION);

      Map<String, Object> scalingGroup = new HashMap<>();
      scalingGroup.put("lifecycleState", "Active");
      scalingGroup.put("maxSize", 10);
      scalingGroup.put("minSize", 1);
      scalingGroup.put("creationTime", "2022-04-01T16:20Z");
      attributes.put("scalingGroup", scalingGroup);

      List<Map> instances = new ArrayList<>();
      Map<String, Object> instance = new HashMap<>();
      instance.put("instanceId", "test-instanceId");
      instance.put("healthStatus", "Healthy");

      instances.add(instance);
      attributes.put("instances", instances);

      Map<String, Object> scalingConfiguration = new HashMap<>();
      scalingConfiguration.put("imageId", "test-imageId");
      attributes.put("scalingConfiguration", scalingConfiguration);

      CacheData cacheData =
          new DefaultCacheData(
              "alicloud:serverGroups:test-serverGroup:test-account:cn-hangzhou:test-serverGroup",
              attributes,
              null);
      return cacheData;
    }
  }

  private class ClustersAnswer implements Answer<CacheData> {
    @Override
    public CacheData answer(InvocationOnMock invocation) throws Throwable {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("application", "test-application");

      Map<String, Collection<String>> relationships = new HashMap<>();
      List<String> serverGroupList = new ArrayList<>();
      serverGroupList.add(
          "alicloud:serverGroups:test-serverGroup:test-account:cn-hangzhou:test-serverGroup");
      relationships.put(SERVER_GROUPS.ns, serverGroupList);

      CacheData cacheData =
          new DefaultCacheData(
              "alicloud:clusters:test-cluster:test-account:test-cluster",
              attributes,
              relationships);
      return cacheData;
    }
  }

  private class ApplicationAnswer implements Answer<CacheData> {
    @Override
    public CacheData answer(InvocationOnMock invocation) throws Throwable {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("name", "test-application");
      Map<String, Collection<String>> relationships = new HashMap<>();
      List<String> clusterList = new ArrayList<>();
      clusterList.add("alicloud:clusters:test-cluster:test-account:test-cluster");
      relationships.put(CLUSTERS.ns, clusterList);
      CacheData cacheData =
          new DefaultCacheData("alicloud:applications:test-application", attributes, relationships);
      return cacheData;
    }
  }
}
