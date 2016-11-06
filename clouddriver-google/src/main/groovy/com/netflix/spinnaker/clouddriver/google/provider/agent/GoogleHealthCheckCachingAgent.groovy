/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.HealthCheck
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.HttpsHealthCheck
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.HEALTH_CHECKS

@Slf4j
class GoogleHealthCheckCachingAgent extends AbstractGoogleCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(HEALTH_CHECKS.ns)
  ] as Set

  String agentType = "$accountName/$GoogleHealthCheckCachingAgent.simpleName"

  GoogleHealthCheckCachingAgent(String clouddriverUserAgentApplicationName,
                                GoogleNamedAccountCredentials credentials,
                                ObjectMapper objectMapper) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleHealthCheck> httpHealthCheckList = loadHealthChecks()
    buildCacheResult(providerCache, httpHealthCheckList)
  }

  /**
   * There are currently three different sets/families of health check endpoints that we cache:
   * 1. /{project}/global/httpHealthChecks/{healthCheckname}
   * 2. /{project}/global/httpsHealthChecks/{healthCheckname}
   * 3. /{project}/global/healthChecks/{healthCheckname}
   *
   * Endpoint (3) can return HTTP and HTTPS endpoints, similar to endpoints (1) and (2).
   * Hence, we have to differentiate which health checks came from which families. We do that with
   * 'kind', which denotes the health check family. Check the GoogleHealthCheck class for the actual values for 'kind'.
   */
  List<GoogleHealthCheck> loadHealthChecks() {
    List<GoogleHealthCheck> ret = []
    def httpHealthChecks = compute.httpHealthChecks().list(project).execute().items as List
    httpHealthChecks.each { HttpHealthCheck hc ->
      ret << new GoogleHealthCheck(
        name: hc.getName(),
        healthCheckType: GoogleHealthCheck.HealthCheckType.HTTP,
        kind: GoogleHealthCheck.HealthCheckKind.httpHealthCheck,
        port: hc.getPort(),
        requestPath: hc.getRequestPath(),
        checkIntervalSec: hc.getCheckIntervalSec(),
        timeoutSec: hc.getTimeoutSec(),
        healthyThreshold: hc.getHealthyThreshold(),
        unhealthyThreshold: hc.getUnhealthyThreshold()
      )
    }

    def httpsHealthChecks = compute.httpsHealthChecks().list(project).execute().items as List
    httpsHealthChecks.each { HttpsHealthCheck hc ->
      ret << new GoogleHealthCheck(
        name: hc.getName(),
        healthCheckType: GoogleHealthCheck.HealthCheckType.HTTPS,
        kind: GoogleHealthCheck.HealthCheckKind.httpsHealthCheck,
        port: hc.getPort(),
        requestPath: hc.getRequestPath(),
        checkIntervalSec: hc.getCheckIntervalSec(),
        timeoutSec: hc.getTimeoutSec(),
        healthyThreshold: hc.getHealthyThreshold(),
        unhealthyThreshold: hc.getUnhealthyThreshold()
      )
    }

    def healthChecks = compute.healthChecks().list(project).execute().items as List
    healthChecks.each { HealthCheck hc ->
      def newHC = new GoogleHealthCheck(
        name: hc.getName(),
        kind: GoogleHealthCheck.HealthCheckKind.healthCheck,
        checkIntervalSec: hc.getCheckIntervalSec(),
        timeoutSec: hc.getTimeoutSec(),
        healthyThreshold: hc.getHealthyThreshold(),
        unhealthyThreshold: hc.getUnhealthyThreshold()
      )

      // Health checks of kind 'healthCheck' are all nested -- the actual health check is contained
      // in a field inside a wrapper HealthCheck object. The wrapper object specifies the type of nested
      // health check as a string, and the proper field is populated based on the type.
      switch(hc.getType()) {
        case 'HTTP':
          newHC.healthCheckType = GoogleHealthCheck.HealthCheckType.HTTP
          newHC.port = hc.getHttpHealthCheck().getPort()
          newHC.requestPath = hc.getHttpHealthCheck().getRequestPath()
          break
        case 'HTTPS':
          newHC.healthCheckType = GoogleHealthCheck.HealthCheckType.HTTPS
          newHC.port = hc.getHttpsHealthCheck().getPort()
          newHC.requestPath = hc.getHttpsHealthCheck().getRequestPath()
          break
        case 'TCP':
          newHC.healthCheckType = GoogleHealthCheck.HealthCheckType.TCP
          newHC.port = hc.getTcpHealthCheck().getPort()
          break
        case 'SSL':
          newHC.healthCheckType = GoogleHealthCheck.HealthCheckType.SSL
          newHC.port = hc.getSslHealthCheck().getPort()
          break
        case 'UDP':
          newHC.healthCheckType = GoogleHealthCheck.HealthCheckType.UDP
          newHC.port = hc.getUdpHealthCheck().getPort()
          break
        default:
          log.warn("Health check ${hc.getName()} has unknown type ${hc.getType()}.")
          return
          break
      }
      ret << newHC
    }
    ret
  }

  private CacheResult buildCacheResult(ProviderCache _, List<GoogleHealthCheck> healthCheckList) {
    log.info("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    healthCheckList.each { GoogleHealthCheck healthCheck ->
      def healthCheckKey = Keys.getHealthCheckKey(accountName, healthCheck.kind as String, healthCheck.getName())

      cacheResultBuilder.namespace(HEALTH_CHECKS.ns).keep(healthCheckKey).with {
        attributes.healthCheck = healthCheck
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(HEALTH_CHECKS.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }
}
