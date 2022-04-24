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
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static com.netflix.spinnaker.clouddriver.alicloud.cache.Keys.Namespace.SUBNETS;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.vpc.model.v20160428.DescribeVSwitchesResponse;
import com.aliyuncs.vpc.model.v20160428.DescribeVSwitchesResponse.VSwitch;
import com.aliyuncs.vpc.model.v20160428.DescribeVpcsResponse;
import com.aliyuncs.vpc.model.v20160428.DescribeVpcsResponse.Vpc;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudSubnetCachingAgentTest extends CommonCachingAgentTest {

  private final String VSWITCHID = "1234567890";

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any()))
        .thenAnswer(new CustomAnswer())
        .thenAnswer(new DescribeVpcsAnswer());
  }

  @Test
  public void testLoadData() {
    AliCloudSubnetCachingAgent agent =
        new AliCloudSubnetCachingAgent(account, REGION, objectMapper, client);
    CacheResult result = agent.loadData(providerCache);
    String key = Keys.getSubnetKey(VSWITCHID, REGION, ACCOUNT);
    List<CacheData> subnets = (List) result.getCacheResults().get(SUBNETS.ns);
    assertEquals(1, subnets.size());
    assertEquals(key, subnets.get(0).getId());
  }

  private class CustomAnswer implements Answer<DescribeVSwitchesResponse> {
    @Override
    public DescribeVSwitchesResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeVSwitchesResponse describeVSwitchesResponse = new DescribeVSwitchesResponse();
      List<VSwitch> vSwitches = new ArrayList<>();
      VSwitch vSwitch = new VSwitch();
      vSwitch.setVSwitchId(VSWITCHID);
      vSwitches.add(vSwitch);
      describeVSwitchesResponse.setVSwitches(vSwitches);
      return describeVSwitchesResponse;
    }
  }

  private class DescribeVpcsAnswer implements Answer<DescribeVpcsResponse> {
    @Override
    public DescribeVpcsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeVpcsResponse response = new DescribeVpcsResponse();
      List<Vpc> vpcs = new ArrayList<>();
      Vpc vpc = new Vpc();
      vpc.setVpcName("test-VpcName");
      vpcs.add(vpc);
      response.setVpcs(vpcs);
      return response;
    }
  }
}
