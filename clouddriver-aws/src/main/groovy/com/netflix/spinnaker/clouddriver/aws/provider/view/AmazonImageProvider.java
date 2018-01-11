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
    } else if (imageIdList.size() > 1) {
      throw new RuntimeException("Image id (" + imageId + ") didn't return an unique image for provider " + getCloudProvider());
    }

    String imageCacheId = imageIdList.get(0);
    CacheData imageCache = cacheView.get(IMAGES.toString(), imageCacheId);

    Artifact image = Artifact.builder()
        .name((String) imageCache.getAttributes().get("name"))
        .type(AmazonImage.AMAZON_IMAGE_TYPE)
        .location(imageCacheId.split(":")[2] + "/" + imageCacheId.split(":")[3])
        .reference((String) imageCache.getAttributes().get("imageId"))
        .metadata(imageCache.getAttributes())
        .build();

    image.getMetadata().put(SERVER_GROUPS.toString(), getServerGroupsBasedOnInstances(imageCache.getRelationships().get(INSTANCES.toString())));
    return Optional.of(image);
  }

  @Override
  public String getCloudProvider() {
    return AmazonCloudProvider.ID;
  }

  private List<Map<String, Object>> getServerGroupsBasedOnInstances(Collection<String> instancesIdList) {
    if (instancesIdList == null) {
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
