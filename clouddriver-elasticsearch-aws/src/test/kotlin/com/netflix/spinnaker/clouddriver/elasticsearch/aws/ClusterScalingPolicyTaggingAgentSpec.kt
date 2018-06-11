/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.elasticsearch.aws

import com.amazonaws.services.autoscaling.model.Alarm
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.amazonaws.services.autoscaling.model.StepAdjustment
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.config.ClusterScalingPolicyTaggingAgentProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue
import java.time.Duration
import java.time.Instant
import java.util.*

class ClusterScalingPolicyTaggingAgentSpec : Spek({

  val retrySupport = RetrySupport()
  val registry = NoopRegistry()
  val objectMapper = ObjectMapper()

  val amazonClientProvider = mock<AmazonClientProvider>()
  val entityTagger = mock<EntityTagger>()

  fun resetMocks() {
    reset(amazonClientProvider, entityTagger)
  }

  describe("running agent for a single account region") {
    val subject = ClusterScalingPolicyTaggingAgent(
      retrySupport,
      registry,
      amazonClientProvider,
      listOf(),
      entityTagger,
      objectMapper,
      ClusterScalingPolicyTaggingAgentProperties()
    )

    val credentials = mock<NetflixAmazonCredentials>()
    val region = mock<AmazonCredentials.AWSRegion>()

    beforeEachTest {
      whenever(credentials.name) doReturn "test"
      whenever(credentials.accountId) doReturn "1234"
      whenever(region.name) doReturn "us-west-2"
    }

    afterEachTest {
      resetMocks()
      reset(credentials, region)
    }

    given("no scaling policies exist") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("orca-test-v000")
      )

      val accountRegion = AccountRegionIndex(
        credentials = credentials,
        region = region,
        serverGroups = serverGroups,
        scalingPolicies = AccountRegionScalingPolicies(
          all = listOf(),
          byServerGroupName = mapOf(),
          byCluster = mapOf()
        ),
        clusterEntityTags = mapOf()
      )

      on("poll cycle") {
        subject.forAccountRegion(accountRegion)

        it("does not tag any entities") {
          verifyNoMoreInteractions(entityTagger)
        }
      }
    }

    given("all server groups are up to date") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("orca-test-v000")
      )
      val clouddriverPolicy = scalingPolicy {
        it.policyARN = "arn-a"
        it.autoScalingGroupName = "clouddriver-test-v000"
        it.policyType = "river"
      }
      val orcaPolicy = scalingPolicy {
        it.policyARN = "arn-b"
        it.autoScalingGroupName = "orca-test-v000"
        it.policyType = "orca"
      }

      val accountRegion = AccountRegionIndex(
        credentials = credentials,
        region = region,
        serverGroups = serverGroups,
        scalingPolicies = AccountRegionScalingPolicies(
          all = listOf(clouddriverPolicy, orcaPolicy),
          byServerGroupName = mapOf(
            "clouddriver-test-v000" to listOf(clouddriverPolicy),
            "orca-test-v000" to listOf(orcaPolicy)
          ),
          byCluster = mapOf(
            "clouddriver-test" to listOf(clouddriverPolicy),
            "orca-test" to listOf(orcaPolicy)
          )
        ),
        clusterEntityTags = mapOf(
          "clouddriver-test" to listOf(
            EntityTags().apply {
              id = "aws:cluster:clouddriver-test:test:us-west-2"
              entityRef = EntityTags.EntityRef().apply {
                entityType = "cluster"
                entityId = "clouddriver-test"
                cloudProvider = "aws"
                accountId = "1234"
                setRegion("us-west-2")
              }
              tags = listOf(
                EntityTags.EntityTag().apply {
                  name = "spinnaker:scaling_policies"
                  namespace = "spinnaker"
                  value = listOf(clouddriverPolicy).toEntityTagValue(objectMapper)
                  valueType = EntityTags.EntityTagValueType.`object`
                }
              )
            }
          ),
          "orca-test" to listOf(
            EntityTags().apply {
              id = "aws:cluster:orca-test:test:us-west-2"
              entityRef = EntityTags.EntityRef().apply {
                entityType = "cluster"
                entityId = "orca-test"
                cloudProvider = "aws"
                accountId = "1234"
                setRegion("us-west-2")
              }
              tags = listOf(
                EntityTags.EntityTag().apply {
                  name = "spinnaker:scaling_policies"
                  namespace = "spinnaker"
                  value = listOf(orcaPolicy).toEntityTagValue(objectMapper)
                  valueType = EntityTags.EntityTagValueType.`object`
                }
              )
            }
          )
        )
      )

      on("poll cycle") {
        subject.forAccountRegion(accountRegion)

        it("ensures the cluster reflects the current scaling policies") {
          verify(entityTagger, times(1)).tag(
            eq("aws"),
            eq("1234"),
            eq("us-west-2"),
            eq("spinnaker"),
            eq("cluster"),
            eq("clouddriver-test"),
            eq("spinnaker:scaling_policies"),
            eq(listOf(clouddriverPolicy).toEntityTagValue(objectMapper)),
            any()
          )
          verify(entityTagger, times(1)).tag(
            eq("aws"),
            eq("1234"),
            eq("us-west-2"),
            eq("spinnaker"),
            eq("cluster"),
            eq("orca-test"),
            eq("spinnaker:scaling_policies"),
            eq(listOf(orcaPolicy).toEntityTagValue(objectMapper)),
            any()
          )
        }
      }
    }

    given("server groups with scaling policies and no cluster entity tags") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("orca-test-v000")
      )
      val clouddriverPolicy1 = scalingPolicy {
        it.policyARN = "arn-a"
        it.autoScalingGroupName = "clouddriver-test-v000"
        it.policyType = "river"
      }
      val clouddriverPolicy2 = scalingPolicy {
        it.policyARN = "arn-a"
        it.autoScalingGroupName = "clouddriver-test-v001"
        it.policyType = "river"
      }
      val orcaPolicy = scalingPolicy {
        it.policyARN = "arn-b"
        it.autoScalingGroupName = "orca-test-v000"
        it.policyType = "orca"
      }

      val accountRegion = AccountRegionIndex(
        credentials = credentials,
        region = region,
        serverGroups = serverGroups,
        scalingPolicies = AccountRegionScalingPolicies(
          all = listOf(clouddriverPolicy1, clouddriverPolicy2, orcaPolicy),
          byServerGroupName = mapOf(
            "clouddriver-test-v000" to listOf(clouddriverPolicy1),
            "clouddriver-test-v001" to listOf(clouddriverPolicy2),
            "orca-test-v000" to listOf(orcaPolicy)
          ),
          byCluster = mapOf(
            "clouddriver-test" to listOf(clouddriverPolicy1),
            "orca-test" to listOf(orcaPolicy)
          )
        ),
        clusterEntityTags = mapOf()
      )

      on("poll cycle") {
        subject.forAccountRegion(accountRegion)

        it("saves cluster scaling policies entity tags") {
          verify(entityTagger, times(1)).tag(
            eq("aws"),
            eq("1234"),
            eq("us-west-2"),
            eq("spinnaker"),
            eq("cluster"),
            eq("clouddriver-test"),
            eq("spinnaker:scaling_policies"),
            eq(listOf(clouddriverPolicy1).toEntityTagValue(objectMapper)),
            any()
          )
          verify(entityTagger, times(1)).tag(
            eq("aws"),
            eq("1234"),
            eq("us-west-2"),
            eq("spinnaker"),
            eq("cluster"),
            eq("orca-test"),
            eq("spinnaker:scaling_policies"),
            eq(listOf(orcaPolicy).toEntityTagValue(objectMapper)),
            any()
          )
        }
      }
    }
  }

  describe("get scaling policies by cluster") {
    val subject = ClusterScalingPolicyTaggingAgent(
      retrySupport,
      registry,
      amazonClientProvider,
      listOf(),
      entityTagger,
      objectMapper,
      ClusterScalingPolicyTaggingAgentProperties()
    )

    afterGroup {
      resetMocks()
    }

    given("multiple server groups in cluster") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("clouddriver-test-v001")
      )
      val scalingPoliciesByServerGroupName = mapOf(
        "clouddriver-test-v000" to listOf(
          scalingPolicy { it.withPolicyARN("arn-a").withPolicyType("simple") },
          scalingPolicy { it.withPolicyARN("arn-b").withPolicyType("targetTracking") },
          scalingPolicy { it.withPolicyARN("arn-c").withPolicyType("step") }
        ),
        "clouddriver-test-v001" to listOf(
          scalingPolicy { it.withPolicyARN("arn-b").withPolicyType("targetTracking") },
          scalingPolicy { it.withPolicyARN("arn-c").withPolicyType("step") }
        )
      )

      on("method call") {
        val result = subject.getScalingPoliciesByCluster(serverGroups, scalingPoliciesByServerGroupName)

        it("de-duplicates policies by arn") {
          expect(result["clouddriver-test"])
            .isNotNull()
            .isA<List<ScalingPolicy>>()
            .map("get scaling policy arns") { map { it.policyType } }
            .assert("no duplicates exist") {
              if (this.subject == listOf("simple", "targetTracking", "step")) {
                pass()
              } else {
                fail()
              }
            }
        }
      }
    }
  }

  describe("shouldUpdateClusterEntityTags method") {
    val subject = ClusterScalingPolicyTaggingAgent(
      retrySupport,
      registry,
      amazonClientProvider,
      listOf(),
      entityTagger,
      objectMapper,
      ClusterScalingPolicyTaggingAgentProperties()
    )

    val credentials = mock<NetflixAmazonCredentials>()
    val region = mock<AmazonCredentials.AWSRegion>()

    beforeGroup {
      whenever(credentials.name) doReturn "test"
      whenever(credentials.accountId) doReturn "1234"
      whenever(region.name) doReturn "us-west-2"
    }

    afterGroup {
      resetMocks()
      reset(credentials, region)
    }

    on("an old server group") {
      val serverGroup = serverGroup("clouddriver-test-v000") {
        it.createdTime = Date.from(Instant.now().minus(Duration.ofDays(30)))
      }
      val boundary = Instant.now()

      it("should update entity tags") {
        expect(subject.shouldUpdateClusterEntityTags(serverGroup, credentials, region, boundary)) {
          isTrue()
        }
      }
    }

    on("a new server group") {
      val serverGroup = serverGroup("clouddriver-test-v000") {
        it.createdTime = Date.from(Instant.now())
      }
      val boundary = Instant.now().minus(Duration.ofHours(1))

      it("should not update entity tags") {
        expect(subject.shouldUpdateClusterEntityTags(serverGroup, credentials, region, boundary)) {
          isFalse()
        }
      }
    }
  }

  describe("scaling policy equality") {
    given("scaling policies that are equal") {
      val policy1 = ScalingPolicy().apply {
        policyARN = "arn-a"
        policyName = "policy1"
        autoScalingGroupName = "clouddriver-test-v000"

        policyType = "simple"
        adjustmentType = "something"
        minAdjustmentStep = 1
        minAdjustmentMagnitude = 1
        scalingAdjustment = 1
        cooldown = 100
        withStepAdjustments(StepAdjustment().apply {
            metricIntervalLowerBound = 1.toDouble()
            metricIntervalUpperBound = 1.toDouble()
            scalingAdjustment = 1
          }
        )
        metricAggregationType = "something"
        withAlarms(Alarm().apply {
          alarmARN = "arn-a"
          alarmName = "alarm"
        })
      }
      val policy2 = ScalingPolicy().apply {
        policyARN = "arn-b"
        policyName = "policy2"
        autoScalingGroupName = "clouddriver-test-v001"

        policyType = "simple"
        adjustmentType = "something"
        minAdjustmentStep = 1
        minAdjustmentMagnitude = 1
        scalingAdjustment = 1
        cooldown = 100
        withStepAdjustments(StepAdjustment().apply {
          metricIntervalLowerBound = 1.toDouble()
          metricIntervalUpperBound = 1.toDouble()
          scalingAdjustment = 1
        }
        )
        metricAggregationType = "something"
        withAlarms(Alarm().apply {
          alarmARN = "arn-a"
          alarmName = "alarm"
        })
      }

      it("evaluates as equal") {
        expect(policy1.isEqual(policy2)).isTrue()
      }
    }

    given("policies that are not equal") {
      val policy1 = ScalingPolicy().apply {
        policyARN = "arn-a"
        policyName = "policy1"
        autoScalingGroupName = "clouddriver-test-v000"

        policyType = "simple"
        adjustmentType = "something"
        minAdjustmentStep = 1
        minAdjustmentMagnitude = 1
        scalingAdjustment = 1
        cooldown = 100
        withStepAdjustments(StepAdjustment().apply {
          metricIntervalLowerBound = 1.toDouble()
          metricIntervalUpperBound = 1.toDouble()
          scalingAdjustment = 1
        }
        )
        metricAggregationType = "something"
        withAlarms(Alarm().apply {
          alarmARN = "arn-a"
          alarmName = "alarm"
        })
      }
      val policy2 = ScalingPolicy().apply {
        policyARN = "arn-b"
        policyName = "policy2"
        autoScalingGroupName = "clouddriver-test-v001"

        policyType = "DIFFERENT"
        adjustmentType = "something"
        minAdjustmentStep = 1
        minAdjustmentMagnitude = 1
        scalingAdjustment = 1
        cooldown = 100
        withStepAdjustments(StepAdjustment().apply {
          metricIntervalLowerBound = 1.toDouble()
          metricIntervalUpperBound = 1.toDouble()
          scalingAdjustment = 1
        }
        )
        metricAggregationType = "something"
        withAlarms(Alarm().apply {
          alarmARN = "arn-a"
          alarmName = "alarm"
        })
      }

      it("evaluates as inequal") {
        expect(policy1.isEqual(policy2)).isFalse()
      }
    }
  }
})

private fun serverGroup(name: String): AutoScalingGroup {
  return serverGroup(name) {
    it.createdTime = Date.from(Instant.now())
  }
}

private fun serverGroup(name: String, fn: ((AutoScalingGroup) -> Unit)?): AutoScalingGroup {
  return AutoScalingGroup().apply {
    withAutoScalingGroupName(name)
    if (fn != null) {
      fn(this)
    }
  }
}

private fun scalingPolicy(fn: (ScalingPolicy) -> Unit): ScalingPolicy {
  return ScalingPolicy().apply(fn)
}
