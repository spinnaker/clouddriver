/*
 * Copyright 2017 Netflix, Inc.
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
import com.netflix.discovery.DiscoveryClient
import com.netflix.dyno.connectionpool.Host
import com.netflix.dyno.connectionpool.HostSupplier
import com.netflix.dyno.connectionpool.TokenMapSupplier
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.dyno.connectionpool.impl.lb.HostToken
import com.netflix.dyno.jedis.DynoJedisClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteCache.CacheMetrics
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteNamedCacheFactory
import com.netflix.spinnaker.cats.dynomite.cluster.DynoClusteredAgentScheduler
import com.netflix.spinnaker.cats.redis.RedisClientDelegate
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.redis.cluster.NodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnExpression('${dynomite.enabled:false}')
@EnableConfigurationProperties([DynomiteConfigurationProperties, RedisConfigurationProperties])
class DynomiteCacheConfig {

  @Bean
  CacheMetrics cacheMetrics(Registry registry) {
    new SpectatorDynomiteCacheMetrics(registry)
  }

  @Bean
  @ConfigurationProperties("dynomite.connectionPool")
  ConnectionPoolConfigurationImpl connectionPoolConfiguration(DynomiteConfigurationProperties dynomiteConfigurationProperties) {
    new ConnectionPoolConfigurationImpl(dynomiteConfigurationProperties.applicationName)
  }

  @Bean(destroyMethod = "stopClient")
  DynoJedisClient dynoJedisClient(DynomiteConfigurationProperties dynomiteConfigurationProperties, ConnectionPoolConfigurationImpl connectionPoolConfiguration, Optional<DiscoveryClient> discoveryClient) {
    def builder = new DynoJedisClient.Builder()
        .withApplicationName(dynomiteConfigurationProperties.applicationName)
        .withDynomiteClusterName(dynomiteConfigurationProperties.clusterName)

    discoveryClient.map({ dc ->
      builder.withDiscoveryClient(dc)
        .withCPConfig(connectionPoolConfiguration)
    }).orElseGet({
      connectionPoolConfiguration
          .withTokenSupplier(new StaticTokenMapSupplier(dynomiteConfigurationProperties.dynoHostTokens))
          .setLocalDataCenter(dynomiteConfigurationProperties.localDataCenter)
          .setLocalRack(dynomiteConfigurationProperties.localRack)

      builder
          .withHostSupplier(new StaticHostSupplier(dynomiteConfigurationProperties.dynoHosts))
          .withCPConfig(connectionPoolConfiguration)
    }).build()
  }

  @Bean
  DynomiteClientDelegate dynomiteClientDelegate(DynoJedisClient dynoJedisClient) {
    new DynomiteClientDelegate(dynoJedisClient)
  }

  @Bean
  @ConfigurationProperties("caching.redis")
  RedisCacheOptions.Builder redisCacheOptionsBuilder() {
    RedisCacheOptions.builder()
  }

  @Bean
  RedisCacheOptions redisCacheOptions(RedisCacheOptions.Builder redisCacheOptionsBuilder) {
    redisCacheOptionsBuilder.build()
  }

  @Bean
  NamedCacheFactory cacheFactory(
    RedisClientDelegate dynomiteClientDelegate,
    ObjectMapper objectMapper,
    RedisCacheOptions redisCacheOptions,
    CacheMetrics cacheMetrics) {
    new DynomiteNamedCacheFactory(dynomiteClientDelegate, objectMapper, redisCacheOptions, cacheMetrics)
  }

  @Bean
  @ConditionalOnMissingBean(NodeStatusProvider.class)
  DefaultNodeStatusProvider nodeStatusProvider() {
    new DefaultNodeStatusProvider()
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider(RedisConfigurationProperties redisConfigurationProperties) {
    new CustomSchedulableAgentIntervalProvider(
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.intervalSeconds),
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.timeoutSeconds)
    );
  }

  @Bean
  @ConditionalOnProperty(value = "caching.writeEnabled", matchIfMissing = true)
  AgentScheduler agentScheduler(RedisClientDelegate redisClientDelegate, AgentIntervalProvider agentIntervalProvider, NodeStatusProvider nodeStatusProvider) {
    new DynoClusteredAgentScheduler(redisClientDelegate, new DefaultNodeIdentity(), agentIntervalProvider, nodeStatusProvider)
  }

  static class StaticHostSupplier implements HostSupplier {

    private final List<Host> hosts

    StaticHostSupplier(List<Host> hosts) {
      this.hosts = hosts
    }

    @Override
    Collection<Host> getHosts() {
      return hosts
    }
  }

  static class StaticTokenMapSupplier implements TokenMapSupplier {

    List<HostToken> hostTokens = new ArrayList<>()

    StaticTokenMapSupplier(List<HostToken> hostTokens) {
      this.hostTokens = hostTokens
    }

    @Override
    List<HostToken> getTokens(Set<Host> activeHosts) {
      return hostTokens
    }

    @Override
    HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
      return hostTokens.find { it.host == host }
    }
  }
}
