/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.view;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonImage;
import com.netflix.spinnaker.clouddriver.model.ImageProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS;


@Component
public class AmazonImageProvider implements ImageProvider {

  private final Cache cacheView;


  @Autowired
  AmazonImageProvider(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @Override
  public Optional<Artifact> getImageById(String imageId) {

    if (!imageId.startsWith("ami-")) {
      throw new RuntimeException("Image Id provided (" + imageId + ") is not a valid id for the provider " + getCloudProvider());
    }

    List<String> imageIdList = new ArrayList<>(cacheView.filterIdentifiers(IMAGES.toString(), "*" + imageId));

    if (imageIdList.isEmpty()) {
      return Optional.empty();
    }

    List<CacheData> imageCacheList = new ArrayList<>(cacheView.getAll(IMAGES.toString(), imageIdList));

    Artifact image = Artifact.builder()
        .name((String) imageCacheList.get(0).getAttributes().get("name"))
        .type(AmazonImage.AMAZON_IMAGE_TYPE)
        .location(imageCacheList.get(0).getAttributes().get("ownerId") + "/" + imageCacheList.get(0).getId().split(":")[3])
        .reference((String) imageCacheList.get(0).getAttributes().get("imageId"))
        .metadata(imageCacheList.get(0).getAttributes())
        .build();

    List<String> instancesIdList = imageCacheList.stream()
        .filter(imageCache -> imageCache.getRelationships().get(INSTANCES.toString()) != null)
        .map(imageCache -> imageCache.getRelationships().get(INSTANCES.toString()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());

    image.getMetadata().put(SERVER_GROUPS.toString(), getServerGroupsBasedOnInstances(instancesIdList));
    return Optional.of(image);
  }

  @Override
  public String getCloudProvider() {
    return AmazonCloudProvider.ID;
  }

  private List<Map<String, Object>> getServerGroupsBasedOnInstances(Collection<String> instancesIdList) {
    if (instancesIdList == null || instancesIdList.isEmpty()) {
      return new ArrayList<>();
    }
    return cacheView.getAll(INSTANCES.toString(), instancesIdList)
        .stream()
        .map(instanceCache -> instanceCache.getRelationships().get(SERVER_GROUPS.toString()))
        .flatMap(Collection::stream)
        .map(serverGroupId -> cacheView.get(SERVER_GROUPS.toString(), serverGroupId).getAttributes())
        .collect(Collectors.toList());
  }
}
