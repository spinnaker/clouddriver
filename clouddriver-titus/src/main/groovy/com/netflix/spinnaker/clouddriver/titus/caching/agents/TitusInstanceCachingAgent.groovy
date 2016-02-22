/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching.agents
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.caching.Keys
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.titus.caching.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

class TitusInstanceCachingAgent implements CachingAgent {

  private static final Logger log = LoggerFactory.getLogger(TitusClusterCachingAgent)

  final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(IMAGES.ns)
  ] as Set)

  private final TitusClientProvider titusClientProvider
  private final NetflixTitusCredentials account
  private final String region
  private final ObjectMapper objectMapper

  TitusInstanceCachingAgent(TitusClientProvider titusClientProvider,
                            NetflixTitusCredentials account,
                            String region,
                            ObjectMapper objectMapper) {
    this.titusClientProvider = titusClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    TitusCachingProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${TitusInstanceCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
    public MutableCacheData(String id) {
      this.id = id
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    // Initialize empty caches
    Closure<Map<String, CacheData>> cache = {
      [:].withDefault { String id -> new MutableCacheData(id) }
    }
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> instances = cache()
    Map<String, CacheData> images = cache()

    // Fetch ALL titus tasks
    TitusClient titusClient = titusClientProvider.getTitusClient(account, region)
    List<Job> jobs = titusClient.getAllJobs()
    for (Job job : jobs) {
      for (Job.TaskSummary task : job.tasks) {
        def data = new InstanceData(job, task, account.name, region)
        cacheImage(data, images)
        cacheServerGroup(data, serverGroups)
        cacheInstance(data, instances)
      }
    }

    new DefaultCacheResult(
      (SERVER_GROUPS.ns): serverGroups.values(),
      (INSTANCES.ns): instances.values(),
      (IMAGES.ns): images.values()
    )
  }

  private void cacheImage(InstanceData data, Map<String, CacheData> images) {
    images[data.imageId].with {
      relationships[INSTANCES.ns].add(data.instanceId)
    }
  }

  private void cacheServerGroup(InstanceData data, Map<String, CacheData> serverGroups) {
    if (data.serverGroup) {
      serverGroups[data.serverGroup].with {
        relationships[INSTANCES.ns].add(data.instanceId)
      }
    }
  }

  private void cacheInstance(InstanceData data, Map<String, CacheData> instances) {
    instances[data.instanceId].with {
      Job.TaskSummary task = objectMapper.convertValue(data.task, Job.TaskSummary)
      attributes.task = task
      Map<String, Object> job = objectMapper.convertValue(data.job, Map)
      job.remove('tasks')
      attributes.job = job
      attributes.put(HEALTH.ns, [getTitusHealth(task)])
      relationships[IMAGES.ns].add(data.imageId)
      if (data.serverGroup) {
        relationships[SERVER_GROUPS.ns].add(data.serverGroup)
      } else {
        relationships[SERVER_GROUPS.ns].clear()
      }
    }

  }

  private Map<String, String> getTitusHealth(Job.TaskSummary task) {
    TaskState taskState = task.state
    HealthState healthState = HealthState.Unknown
    if (taskState in [TaskState.RUNNING]) {
      healthState = HealthState.Up
    } else if (taskState in [TaskState.STOPPED, TaskState.FAILED, TaskState.CRASHED, TaskState.FINISHED, TaskState.DEAD, TaskState.TERMINATING]) {
      healthState = HealthState.Down
    } else if (taskState in [TaskState.STARTING, TaskState.DISPATCHED, TaskState.PENDING, TaskState.QUEUED]) {
      healthState = HealthState.Starting
    } else {
      healthState = HealthState.Unknown
    }
    [type: 'Titus', healthClass: 'platform', state: healthState.toString()]
  }

  private static class InstanceData {
    private final Job job
    private final Job.TaskSummary task
    private final String instanceId
    private final String serverGroup
    private final String imageId

    public InstanceData(Job job, Job.TaskSummary task, String account, String region) {
      this.job = job
      this.task = task
      this.instanceId = Keys.getInstanceKey(task.id, account, region)
      this.serverGroup = job.name
      this.imageId = "${job.applicationName}:${job.version}"
    }
  }
}
