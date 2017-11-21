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

package com.netflix.spinnaker.clouddriver.aws.health

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.AmazonEC2Exception
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.boot.actuate.health.Status
import spock.lang.Specification

class AmazonHealthIndicatorSpec extends Specification {

  def "health fails when amazon appears unreachable"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll(true) >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { throw new AmazonServiceException('fail') }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.OUT_OF_SERVICE
    health.details.error == 'com.netflix.spinnaker.clouddriver.aws.health.AmazonHealthIndicator$AmazonUnreachableException: An error occurred while querying the AWS account foo (123456789012foo)'
  }

  def "health succeeds when amazon is reachable"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll(true) >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    creds[0].enabled
  }

  def "health succeeds when account has permission errors, but account is disabled"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll(true) >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> {
        def exception = new AmazonEC2Exception('fail')
        exception.errorCode = "UnauthorizedOperation"
        throw exception
      }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    !creds[0].enabled
  }

  def "health succeeds when amazon is reachable, and disabled accounts are re-enabled"() {
    setup:
    def creds = [TestCredential.named('foo', [enabled: false])]
    def holder = Stub(AccountCredentialsProvider) {
      getAll(true) >> creds
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }
    def indicator = new AmazonHealthIndicator(accountCredentialsProvider: holder, amazonClientProvider: mockAmazonClientProvider)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    creds[0].enabled
  }
}
