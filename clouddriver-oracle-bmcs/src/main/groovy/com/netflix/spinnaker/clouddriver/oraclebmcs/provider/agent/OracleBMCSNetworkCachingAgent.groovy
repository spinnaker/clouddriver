/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.oraclebmcs.cache.Keys
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSNamedAccountCredentials
import com.oracle.bmc.core.model.Vcn
import com.oracle.bmc.core.requests.ListVcnsRequest
import groovy.util.logging.Slf4j

@Slf4j
class OracleBMCSNetworkCachingAgent extends AbstractOracleBMCSCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AgentDataType.Authority.AUTHORITATIVE.forType(Keys.Namespace.NETWORKS.ns)
  ] as Set

  OracleBMCSNetworkCachingAgent(String clouddriverUserAgentApplicationName,
                                OracleBMCSNamedAccountCredentials credentials,
                                ObjectMapper objectMapper) {
    super(objectMapper, credentials, clouddriverUserAgentApplicationName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<Vcn> vcns = loadVcns()
    return buildCacheResult(vcns)
  }

  List<Vcn> loadVcns() {
    def response = credentials.networkClient.listVcns(ListVcnsRequest.builder()
      .compartmentId(credentials.compartmentId)
      .build())
    return response.items
  }

  private CacheResult buildCacheResult(List<Vcn> vcns) {
    log.info("Describing items in $agentType")

    List<CacheData> data = vcns.collect { Vcn vcn ->
      if (vcn.lifecycleState != Vcn.LifecycleState.Available) {
        return null
      }
      Map<String, Object> attributes = objectMapper.convertValue(vcn, ATTRIBUTES)
      new DefaultCacheData(
        Keys.getNetworkKey(vcn.displayName, vcn.id, credentials.region, credentials.name),
        attributes,
        [:]
      )
    }
    data.removeAll {it == null}
    def cacheData = [(Keys.Namespace.NETWORKS.ns): data]
    log.info("Caching ${data.size()} items in $agentType")
    return new DefaultCacheResult(cacheData, [:])
  }


}
