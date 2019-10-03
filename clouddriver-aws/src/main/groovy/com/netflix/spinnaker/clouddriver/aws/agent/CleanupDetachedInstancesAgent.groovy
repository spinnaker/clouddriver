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

package com.netflix.spinnaker.clouddriver.aws.agent

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.DetachInstancesAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.security.ProviderUtils
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class CleanupDetachedInstancesAgent implements RunnableAgent, CustomScheduledAgent {
  public static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10)
  public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(20)

  final AmazonClientProvider amazonClientProvider
  final AccountCredentialsRepository accountCredentialsRepository
  final long pollIntervalMillis
  final long timeoutMillis

  CleanupDetachedInstancesAgent(AmazonClientProvider amazonClientProvider,
                                AccountCredentialsRepository accountCredentialsRepository) {
    this(amazonClientProvider, accountCredentialsRepository, DEFAULT_POLL_INTERVAL_MILLIS, DEFAULT_TIMEOUT_MILLIS)
  }

  CleanupDetachedInstancesAgent(AmazonClientProvider amazonClientProvider,
                                AccountCredentialsRepository accountCredentialsRepository,
                                long pollIntervalMillis,
                                long timeoutMills) {
    this.amazonClientProvider = amazonClientProvider
    this.accountCredentialsRepository = accountCredentialsRepository
    this.pollIntervalMillis = pollIntervalMillis
    this.timeoutMillis = timeoutMills
  }

  @Override
  String getAgentType() {
    "${CleanupDetachedInstancesAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    return AwsCleanupProvider.PROVIDER_NAME
  }

  @Override
  void run() {
    getAccounts().each { NetflixAmazonCredentials credentials ->
      credentials.regions.each { AmazonCredentials.AWSRegion region ->
        log.info("Looking for instances pending termination in ${credentials.name}:${region.name}")

        def amazonEC2 = amazonClientProvider.getAmazonEC2(credentials, region.name, true)
        def describeInstancesRequest = new DescribeInstancesRequest().withFilters(
          new Filter("tag-key", [DetachInstancesAtomicOperation.TAG_PENDING_TERMINATION])
        )
        while (true) {
          def result = amazonEC2.describeInstances(describeInstancesRequest)

          def instanceIdsToTerminate = []
          result.reservations.each {
            instanceIdsToTerminate.addAll(it.getInstances().findAll { (shouldTerminate(it)) }*.instanceId)
          }

          if (instanceIdsToTerminate) {
            // terminate up to 20 instances at a time (avoids any AWS limits on # of concurrent terminations)
            instanceIdsToTerminate.collate(20).each {
              log.info("Terminating instances in ${credentials.name}/${region.name} (instanceIds: ${it.join(",")})")
              amazonEC2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(it))
              Thread.sleep(500)
            }

          }

          if (result.nextToken) {
            describeInstancesRequest.withNextToken(result.nextToken)
          } else {
            break
          }
        }
      }
    }
  }

  private Set<NetflixAmazonCredentials> getAccounts() {
    ProviderUtils.buildThreadSafeSetOfAccounts(accountCredentialsRepository, NetflixAmazonCredentials, AmazonCloudProvider.ID)
  }

  /**
   * An instance should only be terminated iff:
   * - not already terminated
   * - explicitly tagged for termination
   * - not in an ASG
   */
  static boolean shouldTerminate(Instance instance) {
    def tags = instance.tags
    def isInASG = tags.find { it.key.equalsIgnoreCase("aws:autoscaling:groupName") }
    def isPendingTermination = tags.find {
      it.key.equalsIgnoreCase(DetachInstancesAtomicOperation.TAG_PENDING_TERMINATION)
    }

    return !instance.state?.name?.equalsIgnoreCase("terminated") && !isInASG && isPendingTermination
  }
}
