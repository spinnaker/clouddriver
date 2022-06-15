/*
 * Copyright 2022 Alibaba Group.
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
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
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
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudClusterProviderTest extends CommonProvider {

  @Before
  public void testBefore() {}

  @Test
  public void testGetClusterDetails() {
    when(cacheView.get(eq(APPLICATIONS.ns), anyString())).thenAnswer(new ApplicationAnswer());
    when(cacheView.getAll(eq(CLUSTERS.ns), anyCollection())).thenAnswer(new ClustersAnswer());
    when(cacheView.getAll(eq(SERVER_GROUPS.ns), anyCollection()))
        .thenAnswer(new ServerGroupsAnswer());

    AliCloudClusterProvider provider =
        new AliCloudClusterProvider(objectMapper, cacheView, new AliCloudProvider());
    Map<String, Set<AliCloudCluster>> clusterDetails =
        provider.getClusterDetails("test-application");
    assertEquals(1, clusterDetails.get("test-application").size());
  }

  @Test
  public void testGetCluster() {
    when(cacheView.get(eq(CLUSTERS.ns), anyString())).thenReturn(clusterCacheData());
    when(cacheView.getAll(eq(SERVER_GROUPS.ns), anyCollection()))
        .thenAnswer(new ServerGroupsAnswer());

    AliCloudClusterProvider provider =
        new AliCloudClusterProvider(objectMapper, cacheView, new AliCloudProvider());
    AliCloudCluster cluster =
        provider.getCluster("test-application", ACCOUNT, "test-cluster", true);
    assertNotNull(cluster);
  }

  @Test
  public void testGetServerGroup() {
    when(cacheView.get(eq(SERVER_GROUPS.ns), anyString())).thenReturn(serverGroupCacheData());

    AliCloudClusterProvider provider =
        new AliCloudClusterProvider(objectMapper, cacheView, new AliCloudProvider());
    AliCloudServerGroup serverGroup =
        provider.getServerGroup(ACCOUNT, REGION, "test-cluster", true);
    assertNotNull(serverGroup);
  }

  private class ServerGroupsAnswer implements Answer<Collection<CacheData>> {
    @Override
    public Collection<CacheData> answer(InvocationOnMock invocation) throws Throwable {
      CacheData cacheData = serverGroupCacheData();
      return Lists.newArrayList(cacheData);
    }
  }

  @NotNull
  private CacheData serverGroupCacheData() {
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

  private class ClustersAnswer implements Answer<Collection<CacheData>> {
    @Override
    public Collection<CacheData> answer(InvocationOnMock invocation) throws Throwable {
      CacheData cacheData = clusterCacheData();
      return Lists.newArrayList(cacheData);
    }
  }

  @NotNull
  private CacheData clusterCacheData() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("application", "test-application");

    Map<String, Collection<String>> relationships = new HashMap<>();
    List<String> serverGroupList = new ArrayList<>();
    serverGroupList.add(
        "alicloud:serverGroups:test-serverGroup:test-account:cn-hangzhou:test-serverGroup");
    relationships.put(SERVER_GROUPS.ns, serverGroupList);

    CacheData cacheData =
        new DefaultCacheData(
            "alicloud:clusters:test-cluster:test-account:test-cluster", attributes, relationships);
    return cacheData;
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
