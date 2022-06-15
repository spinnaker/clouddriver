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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
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

public class AliCloudKeyPairCachingAgentTest extends CommonCachingAgentTest {

  private final String KEYPAIRNAME = "kpName";

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any())).thenAnswer(new KeyPairsAnswer());
  }

  @Test
  public void testLoadData() {
    AliCloudKeyPairCachingAgent agent =
        new AliCloudKeyPairCachingAgent(account, REGION, objectMapper, client);
    CacheResult result = agent.loadData(providerCache);

    String key = Keys.getKeyPairKey(KEYPAIRNAME, REGION, ACCOUNT);

    List<CacheData> KeyPairs =
        (List) result.getCacheResults().get(Keys.Namespace.ALI_CLOUD_KEY_PAIRS.ns);

    assertEquals(1, KeyPairs.size());
    assertEquals(key, KeyPairs.get(0).getId());
  }

  private class KeyPairsAnswer implements Answer<DescribeKeyPairsResponse> {
    @Override
    public DescribeKeyPairsResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeKeyPairsResponse response = new DescribeKeyPairsResponse();
      List<KeyPair> keyPairs = new ArrayList<>();
      KeyPair keyPair = new KeyPair();
      keyPair.setKeyPairName(KEYPAIRNAME);
      keyPairs.add(keyPair);
      response.setKeyPairs(keyPairs);
      return response;
    }
  }
}
