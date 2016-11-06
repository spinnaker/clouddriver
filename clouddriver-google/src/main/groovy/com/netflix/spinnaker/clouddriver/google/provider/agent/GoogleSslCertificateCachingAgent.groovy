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
import com.google.api.services.compute.model.SslCertificate
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SSL_CERTIFICATES

@Slf4j
class GoogleSslCertificateCachingAgent extends AbstractGoogleCachingAgent  {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SSL_CERTIFICATES.ns)
  ] as Set

  String agentType = "$accountName/$GoogleSslCertificateCachingAgent.simpleName"

  GoogleSslCertificateCachingAgent(String clouddriverUserAgentApplicationName,
                                   GoogleNamedAccountCredentials credentials,
                                   ObjectMapper objectMapper) {
    super(clouddriverUserAgentApplicationName,
      credentials,
      objectMapper)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<SslCertificate> sslCertificateList = loadSslCertificates()
    buildCacheResult(providerCache, sslCertificateList)
  }

  List<SslCertificate> loadSslCertificates() {
    compute.sslCertificates().list(project).execute().items as List
  }

  private CacheResult buildCacheResult(ProviderCache _, List<SslCertificate> sslCertificateList) {
    log.info("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    sslCertificateList.each { SslCertificate sslCertificate ->
      def sslCertificateKey = Keys.getSslCertificateKey(accountName, sslCertificate.getName())

      cacheResultBuilder.namespace(SSL_CERTIFICATES.ns).keep(sslCertificateKey).with {
        attributes.name = sslCertificate.name
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(SSL_CERTIFICATES.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }
}
