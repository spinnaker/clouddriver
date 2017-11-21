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

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.services.ec2.model.AmazonEC2Exception
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

import java.util.concurrent.atomic.AtomicReference

@Component
class AmazonHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(AmazonHealthIndicator)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AmazonClientProvider amazonClientProvider

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Override
  Health health() {
    def ex = lastException.get()
    if (ex) {
      return new Health.Builder().outOfService().withException(ex).build()
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 120000L)
  void checkHealth() {
    try {
      Set<NetflixAmazonCredentials> amazonCredentials = accountCredentialsProvider.getAll(true).findAll {
        it instanceof NetflixAmazonCredentials
      } as Set<NetflixAmazonCredentials>
      for (NetflixAmazonCredentials credentials in amazonCredentials) {
        try {
          def ec2 = amazonClientProvider.getAmazonEC2(credentials, AmazonClientProvider.DEFAULT_REGION, true)
          if (!ec2) {
            throw new AmazonClientException("Could not create Amazon client for ${credentials.name}")
          }
          ec2.describeAccountAttributes()
          if (!credentials.enabled) {
            LOG.info("The account ${credentials.name} (${credentials.accountId}) is back to healthy")
            credentials.enabled = true
          }
        } catch (AmazonEC2Exception e) {
          credentials.enabled = false
          if (e.getErrorCode() == "UnauthorizedOperation") {
            // Log the error and disable the account credentials, but don't make Clouddriver unhealthy
            LOG.error("Clouddriver is not authorized to access AWS account ${credentials.name} (${credentials.accountId})", e)
          } else {
            throw new AmazonUnreachableException("An error occurred while querying the AWS account ${credentials.name} " +
              "(${credentials.accountId})", e)
          }
        } catch (AmazonServiceException e) {
          credentials.enabled = false
          throw new AmazonUnreachableException("An error occurred while querying the AWS account ${credentials.name} " +
            "(${credentials.accountId})", e)

        }
      }
      lastException.set(null)
    } catch (Exception ex) {
      LOG.error "Unhealthy", ex
      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = 'Could not reach Amazon.')
  @InheritConstructors
  static class AmazonUnreachableException extends RuntimeException {}
}
