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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.alicloud.model.AliCloudSubnet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudSubnetProviderTest extends CommonProvider {

  @Before
  public void testBefore() {
    when(cacheView.filterIdentifiers(anyString(), anyString())).thenAnswer(new FilterAnswer());
    when(cacheView.getAll(anyString(), any(), any())).thenAnswer(new CacheDataAnswer());
  }

  @Test
  public void testGetAll() {
    AliCloudSubnetProvider provider = new AliCloudSubnetProvider(objectMapper, cacheView);
    Set<AliCloudSubnet> all = provider.getAll();
    assertEquals(1, all.size());
  }

  private class FilterAnswer implements Answer<List<String>> {
    @Override
    public List<String> answer(InvocationOnMock invocation) throws Throwable {
      List<String> list = new ArrayList<>();
      list.add("alicloud:subnets:vsw-test:test-account:cn-hangzhou");
      return list;
    }
  }

  private class CacheDataAnswer implements Answer<List<CacheData>> {
    @Override
    public List<CacheData> answer(InvocationOnMock invocation) throws Throwable {
      List<CacheData> cacheDatas = new ArrayList<>();
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("account", ACCOUNT);
      attributes.put("region", REGION);
      attributes.put("status", "Available");
      attributes.put("vpcId", "vpc-test");
      attributes.put("zoneId", "cn-hangzhou-f");
      attributes.put("type", "alicloud");
      attributes.put("vswitchId", "vId");
      attributes.put("vswitchName", "vName");
      CacheData cacheData1 =
          new DefaultCacheData(
              "alicloud:subnets:vsw-test:test-account:cn-hangzhou", attributes, null);
      cacheDatas.add(cacheData1);
      return cacheDatas;
    }
  }
}
