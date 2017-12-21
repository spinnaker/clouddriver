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
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.boot.actuate.health.Status
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

class AmazonHealthIndicatorSpec extends Specification {

  def "health fails when amazon appears unreachable"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll() >> creds
      getCredentials("foo") >> creds[0]
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { throw new AmazonServiceException("fail") }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }
    def counter = new AtomicLong(0)
    def mockRegistry = Stub(Registry) {
      gauge(_, _) >> counter
    }

    def indicator = new AmazonHealthIndicator(holder, mockAmazonClientProvider, mockRegistry)

    when:
    indicator.checkHealth()
    indicator.health()

    then:
    thrown AmazonHealthIndicator.AmazonUnreachableException
    counter.get() == 1
  }

  def "health succeeds when amazon is reachable"() {
    setup:
    def creds = [TestCredential.named('foo')]
    def holder = Stub(AccountCredentialsProvider) {
      getAll() >> creds
      getCredentials("foo") >> creds[0]
    }
    def mockEc2 = Stub(AmazonEC2) {
      describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    }
    def mockAmazonClientProvider = Stub(AmazonClientProvider) {
      getAmazonEC2(*_) >> mockEc2
    }

    def counter = new AtomicLong(0)
    def mockRegistry = Stub(Registry) {
      gauge(_, _) >> counter
    }

    def indicator = new AmazonHealthIndicator(holder, mockAmazonClientProvider, mockRegistry)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    counter.get() == 0
  }
}
