/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static yandex.cloud.api.compute.v1.ImageOuterClass.Image;
import static yandex.cloud.api.compute.v1.ImageServiceOuterClass.ListImagesRequest;
import static yandex.cloud.api.compute.v1.ImageServiceOuterClass.ListImagesResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudImage;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public class YandexImageCachingAgent extends AbstractYandexCachingAgent {
  private String agentType = getAccountName() + "/" + YandexImageCachingAgent.class.getSimpleName();
  private Set<AgentDataType> providedDataTypes =
      Collections.singleton(AUTHORITATIVE.forType(Keys.Namespace.IMAGES.getNs()));

  public YandexImageCachingAgent(YandexCloudCredentials credentials, ObjectMapper objectMapper) {
    super(credentials, objectMapper);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    Collection<CacheData> cacheData =
        Stream.concat(loadImages(getFolder()).stream(), loadImages("standard-images").stream())
            .map(
                image ->
                    new DefaultCacheData(
                        Keys.getImageKey(
                            getAccountName(), image.getId(), image.getFolderId(), image.getName()),
                        getObjectMapper()
                            .convertValue(
                                YandexCloudImage.createFromProto(image), MAP_TYPE_REFERENCE),
                        emptyMap()))
            .collect(Collectors.toList());

    return new DefaultCacheResult(singletonMap(Keys.Namespace.IMAGES.getNs(), cacheData));
  }

  private List<Image> loadImages(String folder) {
    List<Image> images = new ArrayList<>();
    String nextPageToken = "";
    do {
      ListImagesRequest request =
          ListImagesRequest.newBuilder().setFolderId(folder).setPageToken(nextPageToken).build();
      ListImagesResponse response = getCredentials().imageService().list(request);
      images.addAll(response.getImagesList());
      nextPageToken = response.getNextPageToken();
    } while (!Strings.isNullOrEmpty(nextPageToken));
    return images;
  }
}
