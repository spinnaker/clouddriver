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
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse.Image;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import com.netflix.spinnaker.clouddriver.alicloud.provider.AliProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;

public class AliCloudImageCachingAgent implements CachingAgent, AccountAware {

  AliCloudCredentials account;
  String region;
  ObjectMapper objectMapper;
  IAcsClient client;

  public AliCloudImageCachingAgent(
      AliCloudCredentials account, String region, ObjectMapper objectMapper, IAcsClient client) {
    this.account = account;
    this.region = region;
    this.objectMapper = objectMapper;
    this.client = client;
  }

  static final Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<AgentDataType>() {
            {
              add(AUTHORITATIVE.forType(IMAGES.ns));
              add(AUTHORITATIVE.forType(NAMED_IMAGES.ns));
            }
          });

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Map<String, Collection<CacheData>> resultMap = new HashMap<>(16);

    List<Image> images = findAllImages();
    List<Map> listMaps = images.stream()
        .map(image -> objectMapper.convertValue(image, Map.class))
        .collect(Collectors.toList());

    List<CacheData> imageDatas = listMaps.stream()
        .map(map-> new DefaultCacheData(Keys.getImageKey(String.valueOf(map.get("imageId")), account.getName(), region),map, new HashMap<>(16)))
        .collect(Collectors.toList());

    List<CacheData> nameImageDatas = listMaps.stream().map(map-> new DefaultCacheData(
        Keys.getNamedImageKey(account.getName(),String.valueOf(map.get("imageName"))),
        map,
        new HashMap<>(16)))
        .collect(Collectors.toList());


    resultMap.put(IMAGES.ns, imageDatas);
    resultMap.put(NAMED_IMAGES.ns, nameImageDatas);

    return new DefaultCacheResult(resultMap);
  }


  private List<Image> findAllImages() {
    int imagePageNumber = 1;
    int imagePageSize = 50;

    DescribeImagesRequest imagesRequest = new DescribeImagesRequest();
    imagesRequest.setPageSize(imagePageSize);
    List<Image> images = new ArrayList<>(128);
    try {
      while (true) {
        imagesRequest.setPageNumber(imagePageNumber);
        DescribeImagesResponse describeImagesResponse = client.getAcsResponse(imagesRequest);
        if (CollectionUtils.isEmpty(describeImagesResponse.getImages())) {
          break;
        } else {
          imagePageNumber = imagePageNumber + 1;
          images.addAll(describeImagesResponse.getImages());

          if (describeImagesResponse.getImages().size() < imagePageSize) {
            break;
          }
        }
      }
    } catch (ClientException e) {
      e.printStackTrace();
    }
    return images;
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
