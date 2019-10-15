/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.agent.NoopExecutionInstrumentation
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.clouddriver.cache.CacheConfig
import com.netflix.spinnaker.clouddriver.cache.NoopOnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.core.NoopAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.core.NoopCloudProvider
import com.netflix.spinnaker.clouddriver.core.ProjectClustersService
import com.netflix.spinnaker.clouddriver.core.RedisConfig
import com.netflix.spinnaker.clouddriver.core.agent.CleanupPendingOnDemandCachesAgent
import com.netflix.spinnaker.clouddriver.core.agent.ProjectClustersCachingAgent
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider
import com.netflix.spinnaker.clouddriver.model.CloudMetricProvider
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ElasticIpProvider
import com.netflix.spinnaker.clouddriver.model.ImageProvider
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.model.InstanceTypeProvider
import com.netflix.spinnaker.clouddriver.model.KeyPairProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.ManifestProvider
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import com.netflix.spinnaker.clouddriver.model.NoopApplicationProvider
import com.netflix.spinnaker.clouddriver.model.NoopCloudMetricProvider
import com.netflix.spinnaker.clouddriver.model.NoopClusterProvider
import com.netflix.spinnaker.clouddriver.model.NoopElasticIpProvider
import com.netflix.spinnaker.clouddriver.model.NoopImageProvider
import com.netflix.spinnaker.clouddriver.model.NoopInstanceProvider
import com.netflix.spinnaker.clouddriver.model.NoopInstanceTypeProvider
import com.netflix.spinnaker.clouddriver.model.NoopKeyPairProvider
import com.netflix.spinnaker.clouddriver.model.NoopLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.NoopManifestProvider
import com.netflix.spinnaker.clouddriver.model.NoopNetworkProvider
import com.netflix.spinnaker.clouddriver.model.NoopReservationReportProvider
import com.netflix.spinnaker.clouddriver.model.NoopSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.NoopServerGroupManagerProvider
import com.netflix.spinnaker.clouddriver.model.NoopSubnetProvider
import com.netflix.spinnaker.clouddriver.model.ReservationReportProvider
import com.netflix.spinnaker.clouddriver.model.SecurityGroupProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroupManager
import com.netflix.spinnaker.clouddriver.model.ServerGroupManagerProvider
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.clouddriver.names.NamingStrategy
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.ExceptionClassifier
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService
import com.netflix.spinnaker.clouddriver.saga.SagaEvent
import com.netflix.spinnaker.clouddriver.search.ApplicationSearchProvider
import com.netflix.spinnaker.clouddriver.search.NoopSearchProvider
import com.netflix.spinnaker.clouddriver.search.ProjectSearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.executor.SearchExecutorConfig
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.AllowedAccountsValidator
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.web.client.RestTemplate

import javax.inject.Provider
import java.time.Clock

@Configuration
@Import([
  RedisConfig,
  CacheConfig,
  SearchExecutorConfig
])
@PropertySource(value = "classpath:META-INF/clouddriver-core.properties", ignoreResourceNotFound = true)
@EnableConfigurationProperties([ProjectClustersCachingAgentProperties, ExceptionClassifierConfigurationProperties])
class CloudDriverConfig {

  @Bean
  @ConditionalOnMissingBean(Clock)
  Clock clock() {
    Clock.systemDefaultZone()
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer defaultObjectMapperCustomizer() {
    return new Jackson2ObjectMapperBuilderCustomizer() {
      @Override
      void customize(Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder) {
        jacksonObjectMapperBuilder.serializationInclusion(JsonInclude.Include.NON_NULL)
        jacksonObjectMapperBuilder.failOnEmptyBeans(false)
        jacksonObjectMapperBuilder.failOnUnknownProperties(false)
        jacksonObjectMapperBuilder.modules(new Jdk8Module(), new JavaTimeModule(), new KotlinModule())
      }
    }
  }

  @Bean
  ObjectMapperSubtypeConfigurer.SubtypeLocator clouddriverSubtypeLocator() {
    return new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(SagaEvent, [
      "com.netflix.spinnaker.clouddriver.orchestration.sagas"
    ])
  }

  @Bean
  String clouddriverUserAgentApplicationName(Environment environment) {
    return "Spinnaker/${environment.getProperty("Implementation-Version", "Unknown")}"
  }

  @Bean
  @ConfigurationProperties('service-limits')
  ServiceLimitConfigurationBuilder serviceLimitConfigProperties() {
    return new ServiceLimitConfigurationBuilder()
  }

  @Bean
  ServiceLimitConfiguration serviceLimitConfiguration(ServiceLimitConfigurationBuilder serviceLimitConfigProperties) {
    return serviceLimitConfigProperties.build()
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsRepository)
  AccountCredentialsRepository accountCredentialsRepository() {
    new MapBackedAccountCredentialsRepository()
  }

  @Bean
  @ConditionalOnMissingBean(AccountCredentialsProvider)
  AccountCredentialsProvider accountCredentialsProvider(AccountCredentialsRepository accountCredentialsRepository) {
    new DefaultAccountCredentialsProvider(accountCredentialsRepository)
  }

  @Bean
  RestTemplate restTemplate() {
    new RestTemplate()
  }

  @Bean
  @ConditionalOnMissingBean(OnDemandCacheUpdater)
  NoopOnDemandCacheUpdater noopOnDemandCacheUpdater() {
    new NoopOnDemandCacheUpdater()
  }

  @Bean
  @ConditionalOnMissingBean(SearchProvider)
  NoopSearchProvider noopSearchProvider() {
    new NoopSearchProvider()
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  ApplicationSearchProvider applicationSearchProvider(Front50Service front50Service) {
    new ApplicationSearchProvider(front50Service)
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  ProjectSearchProvider projectSearchProvider(Front50Service front50Service) {
    new ProjectSearchProvider(front50Service)
  }

  @Bean
  @ConditionalOnMissingBean(CloudProvider)
  CloudProvider noopCloudProvider() {
    new NoopCloudProvider()
  }

  @Bean
  @ConditionalOnMissingBean(CloudMetricProvider)
  CloudMetricProvider noopCloudMetricProvider() {
    new NoopCloudMetricProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ApplicationProvider)
  ApplicationProvider noopApplicationProvider() {
    new NoopApplicationProvider()
  }

  @Bean
  @ConditionalOnMissingBean(LoadBalancerProvider)
  LoadBalancerProvider noopLoadBalancerProvider() {
    new NoopLoadBalancerProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ManifestProvider)
  ManifestProvider noopManifestProvider() {
    new NoopManifestProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ClusterProvider)
  ClusterProvider noopClusterProvider() {
    new NoopClusterProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ReservationReportProvider)
  ReservationReportProvider noopReservationReportProvider() {
    new NoopReservationReportProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ExecutionInstrumentation)
  ExecutionInstrumentation noopExecutionInstrumentation() {
    new NoopExecutionInstrumentation()
  }

  @Bean
  @ConditionalOnMissingBean(InstanceProvider)
  InstanceProvider noopInstanceProvider() {
    new NoopInstanceProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ImageProvider)
  ImageProvider noopImageProvider() {
    new NoopImageProvider()
  }

  @Bean
  @ConditionalOnMissingBean(InstanceTypeProvider)
  InstanceTypeProvider noopInstanceTypeProvider() {
    new NoopInstanceTypeProvider()
  }

  @Bean
  @ConditionalOnMissingBean(KeyPairProvider)
  KeyPairProvider noopKeyPairProvider() {
    new NoopKeyPairProvider()
  }

  @Bean
  @ConditionalOnMissingBean(SecurityGroupProvider)
  SecurityGroupProvider noopSecurityGroupProvider() {
    new NoopSecurityGroupProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ServerGroupManager)
  ServerGroupManagerProvider noopServerGroupManagerProvider() {
    new NoopServerGroupManagerProvider()
  }

  @Bean
  @ConditionalOnMissingBean(SubnetProvider)
  SubnetProvider noopSubnetProvider() {
    new NoopSubnetProvider()
  }

  @Bean
  @ConditionalOnMissingBean(NetworkProvider)
  NetworkProvider noopVpcProvider() {
    new NoopNetworkProvider()
  }

  @Bean
  @ConditionalOnMissingBean(ElasticIpProvider)
  ElasticIpProvider noopElasticIpProvider() {
    new NoopElasticIpProvider()
  }

  @Bean
  ProjectClustersService projectClustersService(Front50Service front50Service,
                                                ObjectMapper objectMapper,
                                                Provider<List<ClusterProvider>> clusterProviders) {
    return new ProjectClustersService(front50Service, objectMapper, clusterProviders)
  }

  @Bean
  CoreProvider coreProvider(Optional<RedisCacheOptions> redisCacheOptions,
                            Optional<RedisClientDelegate> redisClientDelegate,
                            ApplicationContext applicationContext,
                            ProjectClustersService projectClustersService,
                            ProjectClustersCachingAgentProperties projectClustersCachingAgentProperties) {
    List<Agent> agents = [
      new ProjectClustersCachingAgent(
        projectClustersService,
        projectClustersCachingAgentProperties
      )
    ]

    if (redisCacheOptions.isPresent() && redisClientDelegate.isPresent()) {
      agents.add(new CleanupPendingOnDemandCachesAgent(redisCacheOptions.get(), redisClientDelegate.get(), applicationContext))
    }

    return new CoreProvider(agents)
  }

  @Bean
  @ConditionalOnMissingBean(AtomicOperationConverter)
  AtomicOperationConverter atomicOperationConverter() {
    new NoopAtomicOperationConverter()
  }

  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }

  @Bean
  NamerRegistry namerRegistry(Optional<List<NamingStrategy>> namingStrategies) {
    new NamerRegistry(namingStrategies.orElse([]))
  }

  @Bean
  DescriptionAuthorizer descriptionAuthorizer(Registry registry,
                                              ObjectMapper objectMapper,
                                              Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
                                              SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps) {
    return new DescriptionAuthorizer(
      registry,
      objectMapper,
      fiatPermissionEvaluator,
      opsSecurityConfigProps
    )
  }

  @Bean
  ExceptionClassifier exceptionClassifier(ExceptionClassifierConfigurationProperties properties,
                                          DynamicConfigService dynamicConfigService) {
    return new ExceptionClassifier(properties, dynamicConfigService)
  }
}
