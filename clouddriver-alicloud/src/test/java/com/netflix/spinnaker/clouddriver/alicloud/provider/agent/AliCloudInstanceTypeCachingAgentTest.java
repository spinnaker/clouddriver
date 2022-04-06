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
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudInstanceTypeCachingAgentTest extends CommonCachingAgentTest {

  private final String ZONEID = "zoneId";

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any())).thenAnswer(new ZonesAnswer());
  }

  @Test
  public void testLoadData() {
    AliCloudInstanceTypeCachingAgent agent =
        new AliCloudInstanceTypeCachingAgent(account, REGION, objectMapper, client);
    CacheResult result = agent.loadData(providerCache);
    String key = Keys.getInstanceTypeKey(ACCOUNT, REGION, ZONEID);

    List<CacheData> instanceTypes =
        (List) result.getCacheResults().get(Keys.Namespace.INSTANCE_TYPES.ns);

    assertTrue(instanceTypes.size() == 1);
    assertTrue(key.equals(instanceTypes.get(0).getId()));
  }

  private class ZonesAnswer implements Answer<DescribeAvailableResourceResponse> {
    @Override
    public DescribeAvailableResourceResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeAvailableResourceResponse response = new DescribeAvailableResourceResponse();
      List<AvailableZone> zones = new ArrayList<>();
      AvailableZone zone = new AvailableZone();
      zone.setZoneId(ZONEID);
      zones.add(zone);
      response.setAvailableZones(zones);
      return response;
    }
  }
}
