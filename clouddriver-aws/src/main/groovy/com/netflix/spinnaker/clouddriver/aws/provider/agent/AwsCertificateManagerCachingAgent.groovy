/*
 * Copyright 2021 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.certificatemanager.AWSCertificateManager
import com.amazonaws.services.certificatemanager.model.AWSCertificateManagerException
import com.amazonaws.services.certificatemanager.model.CertificateDetail
import com.amazonaws.services.certificatemanager.model.CertificateSummary
import com.amazonaws.services.certificatemanager.model.DescribeCertificateRequest
import com.amazonaws.services.certificatemanager.model.DescribeCertificateResult
import com.amazonaws.services.certificatemanager.model.ListCertificatesRequest
import com.amazonaws.services.certificatemanager.model.ListCertificatesResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCertificate
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils

import java.time.Duration
import java.time.Instant

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CERTIFICATES

@Slf4j
class AwsCertificateManagerCachingAgent implements CachingAgent, AccountAware {
  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final Id securityTokenExceptionGauge

  protected Instant lastFailure

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(CERTIFICATES.ns)
  ] as Set)

  protected static final Duration RETRY_DELAY = Duration.ofMinutes(10)

  AwsCertificateManagerCachingAgent(AmazonClientProvider amazonClientProvider,
                                    NetflixAmazonCredentials account,
                                    String region,
                                    ObjectMapper objectMapper,
                                    Registry registry) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
    this.registry = registry
    this.securityTokenExceptionGauge = registry.createId("aws.certificateCache.errors",
      "account", account.name,
      "account_id", account.accountId,
      "region", region)
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AwsCertificateManagerCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    if (!lastFailure || lastFailure.isBefore(Instant.now() - RETRY_DELAY)) {
      log.info("Describing items in ${agentType}")
      AWSCertificateManager certificateManager = amazonClientProvider.getAwsCertificateManager(account, region)

      List<CertificateSummary> certificateSummaries = listAllCertificates(certificateManager)

      List<CacheData> data = certificateSummaries.findResults {
        buildCacheData(certificateManager, it)
      }

      log.info("Caching ${data.size()} items in ${agentType}")
      return new DefaultCacheResult([(CERTIFICATES.ns): data])
    }
    new DefaultCacheResult([:])
  }

  private List<CertificateSummary> listAllCertificates(AWSCertificateManager certificateManager) {
    List<CertificateSummary> certificateSummaries = []
    ListCertificatesRequest listCertificatesRequest = new ListCertificatesRequest()

    while (true) {
      try {
        ListCertificatesResult result = certificateManager.listCertificates(listCertificatesRequest)
        registry.gauge(securityTokenExceptionGauge.withTag("operation", "ListCertificates")).set(0)
        certificateSummaries.addAll(result.certificateSummaryList)
        if (result.nextToken) {
          listCertificatesRequest.withNextToken(result.nextToken)
        } else {
          break
        }
      } catch (AWSCertificateManagerException exception) {
        lastFailure = Instant.now()
        log.warn("An error occurred while querying AWS Certificate Manager certificates in account ${account.name} " +
          "(${account.accountId}) in region ${region}. Will not retry for the next ${RETRY_DELAY.toMinutes()} " +
          "minutes. Details: \n${exception.message}")
        registry.gauge(securityTokenExceptionGauge.withTag("operation", "ListCertificates")).set(1)
        break
      }
    }
    certificateSummaries
  }

  private DefaultCacheData buildCacheData(
    AWSCertificateManager certificateManager, CertificateSummary certificateSummary) {
    DescribeCertificateRequest request = new DescribeCertificateRequest()
      .withCertificateArn(certificateSummary.certificateArn)
    try {
      DescribeCertificateResult result = certificateManager.describeCertificate(request)
      registry.gauge(securityTokenExceptionGauge.withTag("operation", "DescribeCertificate")).set(0)
      CertificateDetail acmCertificate = result.certificate
      AmazonCertificate amazonCertificate = translateCertificate(acmCertificate)

      Map<String, Object> attributes = objectMapper.convertValue(amazonCertificate,
        AwsInfrastructureProvider.ATTRIBUTES)

      return new DefaultCacheData(
        Keys.getCertificateKey(amazonCertificate.serverCertificateId, region, account.name, "acm"),
        attributes,
        [:])
    } catch (AWSCertificateManagerException exception) {
      lastFailure = Instant.now()
      log.warn("An error occurred while describing AWS Certificate Manager certificate " +
        "${certificateSummary.certificateArn} in account ${account.name} (${account.accountId}) in region ${region}. " +
        "Will not retry for the next ${RETRY_DELAY.toMinutes()} minutes. Details: \n${exception.message}")
      registry.gauge(securityTokenExceptionGauge.withTag("operation", "DescribeCertificate")).set(1)
      return null
    }
  }

  private static AmazonCertificate translateCertificate(CertificateDetail certificate) {
    new AmazonCertificate(
      expiration: certificate.notAfter,
      path: "",
      serverCertificateId: StringUtils.substringAfter(certificate.certificateArn, ":certificate/"),
      serverCertificateName: certificate.domainName,
      arn: certificate.certificateArn,
      uploadDate: certificate.createdAt)
  }
}
