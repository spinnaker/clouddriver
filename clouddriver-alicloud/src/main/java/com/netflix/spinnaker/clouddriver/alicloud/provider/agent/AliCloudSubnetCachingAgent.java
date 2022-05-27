/*
 * Copyright 2022 Alibaba Group.
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

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.alicloud.cache.Keys.Namespace.SUBNETS;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.vpc.model.v20160428.DescribeVSwitchesRequest;
import com.aliyuncs.vpc.model.v20160428.DescribeVSwitchesResponse;
import com.aliyuncs.vpc.model.v20160428.DescribeVSwitchesResponse.VSwitch;
import com.aliyuncs.vpc.model.v20160428.DescribeVpcsRequest;
import com.aliyuncs.vpc.model.v20160428.DescribeVpcsResponse;
import com.aliyuncs.vpc.model.v20160428.DescribeVpcsResponse.Vpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

public class AliCloudSubnetCachingAgent implements CachingAgent, AccountAware {

  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudSubnetCachingAgent(
      AliCloudCredentials account, String region, ObjectMapper objectMapper, IAcsClient client) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<>() {
            {
              add(AUTHORITATIVE.forType(SUBNETS.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {

    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);
    List<CacheData> datas = new ArrayList<>();
    DescribeVSwitchesRequest describeVSwitchesRequest = new DescribeVSwitchesRequest();
    describeVSwitchesRequest.setPageSize(50);
    DescribeVSwitchesResponse describeVSwitchesResponse;
    try {
      describeVSwitchesResponse = client.getAcsResponse(describeVSwitchesRequest);
      for (VSwitch vSwitch : describeVSwitchesResponse.getVSwitches()) {
        DescribeVpcsRequest describeVpcsRequest = new DescribeVpcsRequest();
        describeVpcsRequest.setVpcId(vSwitch.getVpcId());
        DescribeVpcsResponse describeVpcsResponse = client.getAcsResponse(describeVpcsRequest);
        Vpc vpc = describeVpcsResponse.getVpcs().get(0);
        AliCloudSubnetCachingAgent.InnerVSwitche innerVSwitche =
            transformation(vSwitch, vpc.getVpcName());
        Map<String, Object> attributes = objectMapper.convertValue(innerVSwitche, Map.class);
        CacheData data =
            new DefaultCacheData(
                Keys.getSubnetKey(vSwitch.getVSwitchId(), region, account.getName()),
                attributes,
                new HashMap<>(16));
        datas.add(data);
      }

    } catch (ClientException e) {
      e.printStackTrace();
    }

    resultMap.put(SUBNETS.ns, datas);

    return new DefaultCacheResult(resultMap);
  }

  private InnerVSwitche transformation(VSwitch vSwitch, String vpcName) {
    InnerVSwitche innerVSwitche = new InnerVSwitche();
    innerVSwitche.setAccount(account.getName());
    innerVSwitche.setRegion(region);
    innerVSwitche.setStatus(vSwitch.getStatus());
    innerVSwitche.setVpcId(vSwitch.getVpcId());
    innerVSwitche.setVSwitchId(vSwitch.getVSwitchId());
    innerVSwitche.setVSwitchName(vSwitch.getVSwitchName());
    innerVSwitche.setZoneId(vSwitch.getZoneId());
    innerVSwitche.setVpcName(vpcName);
    return innerVSwitche;
  }

  @Data
  class InnerVSwitche {
    private String account;
    private String region;
    private String status;
    private String vSwitchId;
    private String vSwitchName;
    private String vpcId;
    private String zoneId;
    private String type = AliCloudProvider.ID;
    private String vpcName;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + this.getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AliProvider.PROVIDER_NAME;
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }
}
