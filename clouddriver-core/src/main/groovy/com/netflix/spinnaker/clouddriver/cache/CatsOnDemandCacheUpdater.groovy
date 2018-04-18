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

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentLock
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
@Slf4j
class CatsOnDemandCacheUpdater implements OnDemandCacheUpdater {

  private final List<Provider> providers
  private final CatsModule catsModule

  @Autowired
  AgentScheduler agentScheduler

  @Autowired
  public CatsOnDemandCacheUpdater(List<Provider> providers, CatsModule catsModule) {
    this.providers = providers
    this.catsModule = catsModule
  }

  private Collection<OnDemandAgent> getOnDemandAgents() {
    providers.collect {
      it.agents.findAll { it instanceof OnDemandAgent } as Collection<OnDemandAgent>
    }.flatten()
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    onDemandAgents.any { it.handles(type, cloudProvider) }
  }

  @Override
  OnDemandCacheResult handle(OnDemandAgent.OnDemandType type, String cloudProvider, Map<String, ?> data) {
    Collection<OnDemandAgent> onDemandAgents = onDemandAgents.findAll { it.handles(type, cloudProvider) }
    return handle(type, onDemandAgents, data)
  }

  OnDemandCacheResult handle(OnDemandAgent.OnDemandType type, Collection<OnDemandAgent> onDemandAgents, Map<String, ? extends Object> data) {
    boolean hasOnDemandResults = false
    Map<String, List<String>> cachedIdentifiersByType = [:].withDefault { [] }

    for (OnDemandAgent agent : onDemandAgents) {
      try {
        AgentLock lock = null;
        if (agentScheduler.atomic && !(lock = agentScheduler.tryLock((Agent) agent))) {
          hasOnDemandResults = true // force Orca to retry
          continue;
        }
        final long startTime = System.nanoTime()
        def providerCache = catsModule.getProviderRegistry().getProviderCache(agent.providerName)
        agent.metricsSupport.countOnDemand()
        OnDemandAgent.OnDemandResult result = agent.handle(providerCache, data)
        if (result) {
          if (agentScheduler.atomic && !(agentScheduler.lockValid(lock))) {
            hasOnDemandResults = true // force Orca to retry
            continue;
          }
          if (!agent.metricsSupport) {
            hasOnDemandResults = false
            continue;
          }
          if (result.cacheResult) {
            hasOnDemandResults = !(result.cacheResult.cacheResults ?: [:]).values().flatten().isEmpty() && !agentScheduler.atomic
            if (hasOnDemandResults) {
              result.cacheResult.cacheResults.each { k, v ->
                if (v) {
                  cachedIdentifiersByType[k].addAll(v*.id)
                }
              }
            }
            agent.metricsSupport.cacheWrite {
              providerCache.putCacheResult(result.sourceAgentType, result.authoritativeTypes, result.cacheResult)
            }
          }
          if (result.evictions) {
            agent.metricsSupport.cacheEvict {
              result.evictions.each { String evictType, Collection<String> ids ->
                providerCache.evictDeletedItems(evictType, ids)
              }
            }
          }
          if (agentScheduler.atomic && !(agentScheduler.tryRelease(lock))) {
            throw new IllegalStateException("We likely just wrote stale data. If you're seeing this, file a github issue: https://github.com/spinnaker/spinnaker/issues")
          }
          final long elapsed = System.nanoTime() - startTime
          agent.metricsSupport.recordTotalRunTimeNanos(elapsed)
          log.info("$agent.providerName/$agent?.onDemandAgentType handled $type in ${TimeUnit.NANOSECONDS.toMillis(elapsed)} millis. Payload: $data")
        }
      } catch (e) {
        if (agent.metricsSupport != null) {
          agent.metricsSupport.countError()
        }
        log.warn("$agent.providerName/$agent.onDemandAgentType failed to handle on demand update for $type", e)
      }
    }

    if (hasOnDemandResults) {
      return new OnDemandCacheResult(
        status: OnDemandCacheStatus.PENDING,
        cachedIdentifiersByType: cachedIdentifiersByType
      )
    }

    return new OnDemandCacheResult(
      status: OnDemandCacheStatus.SUCCESSFUL
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(OnDemandAgent.OnDemandType type, String cloudProvider) {
    if (agentScheduler.atomic) {
      return []
    }

    Collection<OnDemandAgent> onDemandAgents = onDemandAgents.findAll { it.handles(type, cloudProvider) }
    return onDemandAgents.collect {
      def providerCache = catsModule.getProviderRegistry().getProviderCache(it.providerName)
      it.pendingOnDemandRequests(providerCache)
    }.flatten()
  }

  @Override
  Map pendingOnDemandRequest(OnDemandAgent.OnDemandType type, String cloudProvider, String id) {
    if (agentScheduler.atomic) {
      return null
    }

    Collection<OnDemandAgent> onDemandAgents = onDemandAgents.findAll { it.handles(type, cloudProvider) }
    return onDemandAgents.findResults {
      def providerCache = catsModule.getProviderRegistry().getProviderCache(it.providerName)
      it.pendingOnDemandRequest(providerCache, id)
    }?.getAt(0)
  }
}
