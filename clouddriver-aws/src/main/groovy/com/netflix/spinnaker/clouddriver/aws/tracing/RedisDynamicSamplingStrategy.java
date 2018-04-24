/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.tracing;

import com.amazonaws.xray.strategy.sampling.SamplingRule;
import com.amazonaws.xray.strategy.sampling.SamplingRuleManifest;
import com.amazonaws.xray.strategy.sampling.SamplingStrategy;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Allows loading dynamic sampling rules from Redis.
 *
 * This is largely lifted from LocalizedSamplingStrategy, all the way down to
 * the Yoda conditionals, which only allows rules from a file location. :(
 *
 * If any rules are defined in Redis, it is treated like a full rule
 * definition. The fallback manifest will only be used if no key in Redis
 * exists.
 */
public class RedisDynamicSamplingStrategy implements SamplingStrategy {

  private final static Logger log = LoggerFactory.getLogger(RedisDynamicSamplingStrategy.class);

  private final static String CLIENT_NAME = "tracing";
  private final static String KEY = "{tracing:xray}:samplingRules";

  private final RedisClientSelector redisClientSelector;
  private final SamplingRuleManifest fallbackManifest;
  private final Scheduler scheduler;

  private final ObjectMapper mapper = new ObjectMapper()
    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(JsonParser.Feature.ALLOW_COMMENTS, true);

  private String cache;
  private SamplingRuleManifest cachedManifest;

  public RedisDynamicSamplingStrategy(RedisClientSelector redisClientSelector,
                                      SamplingRuleManifest fallbackManifest) {
    this.redisClientSelector = redisClientSelector;
    this.fallbackManifest = fallbackManifest;
    this.scheduler = Schedulers.io();
  }

  @PostConstruct
  public void init() {
    scheduler.createWorker().schedule(this::updateSamplingRules, 1, TimeUnit.MINUTES);
  }

  private void updateSamplingRules() {
    String newManifest = redisClientSelector.primary(CLIENT_NAME).withCommandsClient(c -> { return c.get(KEY); });

    // If the manifest is empty, clear.
    if (newManifest == null) {
      cache = null;
      cachedManifest = null;
      return;
    }

    // No changes to the cache.
    if (newManifest.equals(cache)) {
      return;
    }

    SamplingRuleManifest manifest;
    try {
      manifest = mapper.readValue(newManifest, SamplingRuleManifest.class);
    } catch (IOException e) {
      log.error("Could not parse Redis rules manifest", e);
      return;
    }

    try {
      validateRuleManifest(manifest);
    } catch (InvalidSamplingRuleManifest e) {
      log.error("Cannot update rule manifest", e);
      return;
    }

    cachedManifest = manifest;
  }

  private void validateRuleManifest(SamplingRuleManifest manifest) {
    if (null != manifest) {
      SamplingRule defaultRule = manifest.getDefaultRule();
      if (null != defaultRule) {
        if (null != defaultRule.getUrlPath() || null != defaultRule.getServiceName() || null != defaultRule.getHttpMethod()) {
          throw new InvalidSamplingRuleManifest("The default rule must not specify values for url_path, service_name, or http_method.");
        } else if (defaultRule.getFixedTarget() < 0 || defaultRule.getRate() < 0) {
          throw new InvalidSamplingRuleManifest("The default rule must specify non-negative values for fixed_target and rate.");
        } else if (manifest.getVersion() != 1) {
          throw new InvalidSamplingRuleManifest("Manifest version: " + manifest.getVersion() + " is not supported.");
        }
        if (null != manifest.getRules()) {
          manifest.getRules().forEach( (rule) -> {
            if (null == rule.getUrlPath() || null == rule.getServiceName() || null == rule.getHttpMethod()) {
              throw new InvalidSamplingRuleManifest("All rules must have values for url_path, service_name, and http_method.");
            } else if (rule.getFixedTarget() < 0 || rule.getRate() < 0) {
              throw new InvalidSamplingRuleManifest("All rules must have non-negative values for fixed_target and rate.");
            }
          });
        } else {
          manifest.setRules(new ArrayList<>());
        }
      } else {
        throw new InvalidSamplingRuleManifest("A default rule must be provided.");
      }
    }
  }

  @Override
  public boolean shouldTrace(String serviceName, String path, String method) {
    return shouldTrace(cachedManifest == null ? fallbackManifest : cachedManifest, serviceName, path, method);
  }

  private boolean shouldTrace(SamplingRuleManifest manifest, String serviceName, String path, String method) {
    SamplingRule firstApplicableRule = null;
    if (null != manifest.getRules()) {
      firstApplicableRule = manifest.getRules().stream()
        .filter(rule -> rule.appliesTo(serviceName, path, method))
        .findFirst()
        .orElse(null);
    }
    return null == firstApplicableRule ? shouldTrace(manifest.getDefaultRule()) : shouldTrace(firstApplicableRule);
  }

  private boolean shouldTrace(SamplingRule samplingRule) {
    return samplingRule.getReservoir().take() || ThreadLocalRandom.current().nextFloat() < samplingRule.getRate();
  }

  @Override
  public boolean isForcedSamplingSupported() {
    return false;
  }

  private static class InvalidSamplingRuleManifest extends RuntimeException {
    InvalidSamplingRuleManifest(String message) {
      super(message);
    }
  }
}
