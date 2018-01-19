/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SCALABLE_TARGETS;

@Component
public class ScalableTargetCacheClient extends AbstractCacheClient<ScalableTarget> {
  private final ObjectMapper objectMapper;

  @Autowired
  public ScalableTargetCacheClient(Cache cacheView, ObjectMapper objectMapper) {
    super(cacheView, SCALABLE_TARGETS.toString());
    this.objectMapper = objectMapper;
  }

  @Override
  protected ScalableTarget convert(CacheData cacheData) {
    ScalableTarget scalableTarget;
    scalableTarget = objectMapper.convertValue(cacheData.getAttributes(), ScalableTarget.class);
    return scalableTarget;
  }

}
