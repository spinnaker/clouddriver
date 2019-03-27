/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching;

import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static java.lang.Math.toIntExact;

@Component
@Slf4j
public class KubernetesV2CacheConfig {
      //sudhakaropsmx:To make logical cache configurable
	  private static int cacheTtlSeconds = toIntExact(TimeUnit.MINUTES.toSeconds(10));
	
	  @Value("${kubernetes.v2.cacheTtl}")
	  public void setExternalLogicalTtlSeconds(String externalLogicalTtlSeconds) {
		if(externalLogicalTtlSeconds != null) {
			cacheTtlSeconds =  toIntExact(Integer.valueOf(externalLogicalTtlSeconds));
		}
	  }
	  public static int getCacheTtlSeconds(){
		  return cacheTtlSeconds;
	  }
}
