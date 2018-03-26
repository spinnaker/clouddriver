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

package com.netflix.spinnaker.clouddriver.titus

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.titus.client.RegionScopedTitusAutoscalingClient
import com.netflix.spinnaker.clouddriver.titus.client.RegionScopedTitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusJobCustomizer
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.v3client.RegionScopedV3TitusClient
import groovy.transform.Immutable

import java.util.concurrent.ConcurrentHashMap

class TitusClientProvider {

  private final Map<TitusClientKey, TitusClient> titusClients = new ConcurrentHashMap<>()
  private final Map<TitusClientKey, TitusAutoscalingClient> titusAutoscalingClients = new ConcurrentHashMap<>()
  private final Registry registry
  private final List<TitusJobCustomizer> titusJobCustomizers

  TitusClientProvider(Registry registry, List<TitusJobCustomizer> titusJobCustomizers) {
    this.registry = registry
    this.titusJobCustomizers = titusJobCustomizers == null ? Collections.emptyList() : Collections.unmodifiableList(titusJobCustomizers)
  }

  TitusClient getTitusClient(NetflixTitusCredentials account, String region) {
    final TitusRegion titusRegion = Objects.requireNonNull(account.regions.find { it.name == region }, "region")
    final TitusClientKey key = new TitusClientKey(Objects.requireNonNull(account.name), titusRegion)
    return titusClients.computeIfAbsent(key, { k -> k.region.apiVersion == '3' ? new RegionScopedV3TitusClient(k.region, registry, titusJobCustomizers, account.environment, account.eurekaName) : new RegionScopedTitusClient(k.region, registry, titusJobCustomizers) })
  }

  TitusAutoscalingClient getTitusAutoscalingClient(NetflixTitusCredentials account, String region) {
    final TitusRegion titusRegion = Objects.requireNonNull(account.regions.find { it.name == region }, "region")
    if (titusRegion.apiVersion != '3' || !account.eurekaName) {
      return null
    }
    final TitusClientKey key = new TitusClientKey(Objects.requireNonNull(account.name), titusRegion)
    return titusAutoscalingClients.computeIfAbsent(key, { k -> new RegionScopedTitusAutoscalingClient(k.region, registry, account.environment, account.eurekaName) })
  }

  @Immutable(knownImmutableClasses = [TitusRegion])
  static class TitusClientKey {
    final String account
    final TitusRegion region
  }
}
