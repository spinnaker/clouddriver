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
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration
import java.time.Instant

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CERTIFICATES

class AwsCertificateManagerCachingAgentSpec extends Specification {
  static final String account = "test-account"
  static final String accountId = "123456789012"
  static final String region = "us-west-2"

  AWSCertificateManager certificateManager = Mock(AWSCertificateManager)

  NetflixAmazonCredentials creds = Stub(NetflixAmazonCredentials) {
    getName() >> account
    it.getAccountId() >> accountId
  }

  AmazonClientProvider amazonClientProvider = Stub(AmazonClientProvider) {
    getAwsCertificateManager(creds, region) >> certificateManager
  }

  ProviderCache providerCache = Mock(ProviderCache)

  ObjectMapper amazonObjectMapper = new AmazonObjectMapperConfigurer().createConfigured()

  Id exceptionGauge = Mock(Id)

  Registry registry = Stub(Registry) {
    createId("aws.certificateCache.errors",
      "account", account,
      "account_id", accountId,
      "region", region) >> exceptionGauge
  }

  String certificateArn1 = "arn:aws:acm:::certificate/certificate1"
  CertificateDetail certificateDetail1 = new CertificateDetail()
    .withCertificateArn(certificateArn1)
    .withDomainName("www.domain.example.com")
    .withNotAfter(new Date())
    .withCreatedAt(new Date())
  CertificateSummary certificateSummary1 = new CertificateSummary()
    .withCertificateArn(certificateArn1)
  DescribeCertificateResult certificateResult1 = new DescribeCertificateResult()
    .withCertificate(certificateDetail1)

  String certificateArn2 = "arn:aws:acm:::certificate/certificate2"
  CertificateDetail certificateDetail2 = new CertificateDetail()
    .withCertificateArn(certificateArn2)
    .withDomainName("*.example.com")
    .withNotAfter(new Date())
    .withCreatedAt(new Date())
  CertificateSummary certificateSummary2 = new CertificateSummary()
    .withCertificateArn(certificateArn2)
  DescribeCertificateResult certificateResult2 = new DescribeCertificateResult()
    .withCertificate(certificateDetail2)

  @Subject
  AwsCertificateManagerCachingAgent agent = new AwsCertificateManagerCachingAgent(
    amazonClientProvider, creds, region, amazonObjectMapper, registry)

  void setup() {
    agent.lastFailure = null
  }

  void "loadData retrieves all ACM certificates from a given account and region"() {
    when:
    def result = agent.loadData(providerCache)

    then:
    1 * certificateManager.listCertificates(new ListCertificatesRequest()) >> new ListCertificatesResult()
      .withCertificateSummaryList([certificateSummary1])
      .withNextToken("token")
    1 * certificateManager.listCertificates(new ListCertificatesRequest().withNextToken("token")) >>
      new ListCertificatesResult().withCertificateSummaryList([certificateSummary2])

    1 * certificateManager.describeCertificate(new DescribeCertificateRequest()
      .withCertificateArn(certificateArn1)) >> certificateResult1
    1 * certificateManager.describeCertificate(new DescribeCertificateRequest()
      .withCertificateArn(certificateArn2)) >> certificateResult2

    with(result.cacheResults.get(CERTIFICATES.ns)) { Collection<CacheData> cd ->
      cd.size() == 2
      cd.find {
        it.id == Keys.getCertificateKey("certificate1", region, account, "acm")
      }.attributes == [
        "expiration"           : certificateDetail1.notAfter.toTimestamp().time,
        "path"                 : "",
        "serverCertificateId"  : "certificate1",
        "serverCertificateName": certificateDetail1.domainName,
        "arn"                  : certificateDetail1.certificateArn,
        "uploadDate"           : certificateDetail1.createdAt.toTimestamp().time
      ]
      cd.find {
        it.id == Keys.getCertificateKey("certificate2", region, account, "acm")
      }.attributes == [
        "expiration"           : certificateDetail2.notAfter.toTimestamp().time,
        "path"                 : "",
        "serverCertificateId"  : "certificate2",
        "serverCertificateName": certificateDetail2.domainName,
        "arn"                  : certificateDetail2.certificateArn,
        "uploadDate"           : certificateDetail2.createdAt.toTimestamp().time
      ]
    }
  }

  void "loadData returns an empty list if the lastFailure is non-null and the retry delay has not yet passed"() {
    given:
    agent.lastFailure = Instant.now()

    when:
    def result = agent.loadData(providerCache)

    then:
    result.cacheResults == [:]
    0 * certificateManager._
  }

  void "loadData retrieves all ACM certificates in a given account and region if lastFailure is non-null and the retry delay has passed"() {
    given:
    agent.lastFailure = Instant.now() - AwsCertificateManagerCachingAgent.RETRY_DELAY - Duration.ofMillis(1)

    when:
    def result = agent.loadData(providerCache)

    then:
    1 * certificateManager.listCertificates(new ListCertificatesRequest()) >> new ListCertificatesResult()
      .withCertificateSummaryList([certificateSummary1])

    1 * certificateManager.describeCertificate(new DescribeCertificateRequest()
      .withCertificateArn(certificateArn1)) >> certificateResult1

    result.cacheResults.get(CERTIFICATES.ns).size() == 1
  }

  void "loadData stops calling listCertificates if it encounters an exception"() {
    when:
    def result = agent.loadData(providerCache)

    then:
    1 * certificateManager.listCertificates(new ListCertificatesRequest()) >> new ListCertificatesResult()
      .withCertificateSummaryList([certificateSummary1])
      .withNextToken("token")
    1 * certificateManager.listCertificates(new ListCertificatesRequest().withNextToken("token")) >> {
      throw new AWSCertificateManagerException("boom!")
    }

    1 * certificateManager.describeCertificate(new DescribeCertificateRequest()
      .withCertificateArn(certificateArn1)) >> certificateResult1

    result.cacheResults.get(CERTIFICATES.ns).size() == 1
  }

  void "loadData skips any certificate for which the DescribeCertificate request fails"() {
    when:
    def result = agent.loadData(providerCache)

    then:
    1 * certificateManager.listCertificates(new ListCertificatesRequest()) >> new ListCertificatesResult()
      .withCertificateSummaryList([certificateSummary1, certificateSummary2])

    1 * certificateManager.describeCertificate(new DescribeCertificateRequest()
      .withCertificateArn(certificateArn1)) >> { throw new AWSCertificateManagerException("boom!") }
    1 * certificateManager.describeCertificate(new DescribeCertificateRequest()
      .withCertificateArn(certificateArn2)) >> certificateResult2

    with(result.cacheResults.get(CERTIFICATES.ns)) { Collection<CacheData> cd ->
      cd.size() == 1
      cd.find {
        it.id == Keys.getCertificateKey("certificate2", region, account, "acm")
      }
    }
  }
}
