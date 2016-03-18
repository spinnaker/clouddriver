/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.redis.JedisPoolSource
import com.netflix.spinnaker.cats.redis.JedisSource
import com.netflix.spinnaker.cats.redis.cache.RedisCache
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.cats.redis.cache.RedisNamedCacheFactory
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnProperty('redis.connection')
class RedisCacheConfig {

  @Bean
  JedisSource jedisSource(JedisPool jedisPool) {
    new JedisPoolSource(jedisPool)
  }

  @Bean
  @ConfigurationProperties("caching.redis")
  RedisCacheOptions.Builder redisCacheOptionsBuilder() {
    return RedisCacheOptions.builder();
  }

  @Bean
  RedisCacheOptions redisCacheOptions(RedisCacheOptions.Builder redisCacheOptionsBuilder) {
    return redisCacheOptionsBuilder.build()
  }

  @Bean
  RedisCache.CacheMetrics cacheMetrics(final Registry registry) {
    return new RedisCache.CacheMetrics() {
      @Override
      void merge(String prefix, String type, int itemCount, int keysWritten, int relationshipCount, int hashMatches, int hashUpdates, int saddOperations, int msetOperations, int hmsetOperations, int pipelineOperations, int expireOperations) {
        final String[] tags = ['prefix', prefix, 'type', type]
        registry.counter('caching.redis.merge.items', tags).increment(itemCount)
        registry.counter('caching.redis.merge.keysWritten', tags).increment(keysWritten)
        registry.gauge(registry.createId('caching.redis.merge.relationshipCount', tags), Integer.valueOf(relationshipCount))
        registry.counter('caching.redis.merge.hashMatches', tags).increment(hashMatches)
        registry.counter('caching.redis.merge.hashUpdates', tags).increment(hashUpdates)
        registry.counter('caching.redis.merge.saddOperations', tags).increment(saddOperations)
        registry.counter('caching.redis.merge.msetOperations', tags).increment(msetOperations)
        registry.counter('caching.redis.merge.hmsetOperations', tags).increment(hmsetOperations)
        registry.counter('caching.redis.merge.pipelineOperations', tags).increment(pipelineOperations)
        registry.counter('caching.redis.merge.expireOperations', tags).increment(expireOperations)
      }

      @Override
      void evict(String prefix, String type, int itemCount, int keysDeleted, int hashesDeleted, int delOperations, int hdelOperations, int sremOperations) {
        final String[] tags = ['prefix', prefix, 'type', type]
        registry.counter('caching.redis.evict.items', tags).increment(itemCount)
        registry.counter('caching.redis.evict.keysDeleted', tags).increment(keysDeleted)
        registry.counter('caching.redis.evict.hashesDeleted', tags).increment(hashesDeleted)
        registry.counter('caching.redis.evict.delOperations', tags).increment(delOperations)
        registry.counter('caching.redis.evict.hdelOperations', tags).increment(hdelOperations)
        registry.counter('caching.redis.evict.sremOperations', tags).increment(sremOperations)
      }

      @Override
      void get(String prefix, String type, int itemCount, int requestedSize, int keysRequested, int relationshipsRequested, int mgetOperationCount) {
        final String[] tags = ['prefix', prefix, 'type', type]
        registry.counter('caching.redis.get.items', tags).increment(itemCount)
        registry.distributionSummary('caching.redis.get.requestedSize', tags).record(requestedSize)
        registry.counter('caching.redis.get.keysRequested', tags).increment(keysRequested)
        registry.counter('caching.redis.get.relationshipsRequested', tags).increment(relationshipsRequested)
        registry.counter('caching.redis.get.mgetOperationCount', tags).increment(mgetOperationCount)
      }
    }
  }

  @Bean
  NamedCacheFactory cacheFactory(
    JedisSource jedisSource,
    ObjectMapper objectMapper,
    RedisCacheOptions redisCacheOptions,
    RedisCache.CacheMetrics cacheMetrics) {
    new RedisNamedCacheFactory(jedisSource, objectMapper, redisCacheOptions, cacheMetrics)
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider(@Value('${redis.poll.intervalSeconds:30}') int pollIntervalSeconds, @Value('${redis.poll.timeoutSeconds:300}') int pollTimeoutSeconds) {
    new CustomSchedulableAgentIntervalProvider(TimeUnit.SECONDS.toMillis(pollIntervalSeconds), TimeUnit.SECONDS.toMillis(pollTimeoutSeconds))
  }

  @Bean
  @ConditionalOnProperty(value = 'caching.writeEnabled', matchIfMissing = true)
  AgentScheduler agentScheduler(JedisSource jedisSource, @Value('${redis.connection:redis://localhost:6379}') String redisConnection, AgentIntervalProvider agentIntervalProvider) {
    URI redisUri = URI.create(redisConnection)
    String redisHost = redisUri.getHost()
    int redisPort = redisUri.getPort()
    if (redisPort == -1) {
      redisPort = 6379
    }
    new ClusteredAgentScheduler(jedisSource, new DefaultNodeIdentity(redisHost, redisPort), agentIntervalProvider)
  }
}
