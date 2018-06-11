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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials.AWSRegion
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.clouddriver.tags.EntityTagger.ENTITY_TYPE_CLUSTER
import com.netflix.spinnaker.config.ClusterScalingPolicyTaggingAgentProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Tracks and reports on scaling policies for clusters.
 *
 * Indexes scaling policies on a cluster level and alarms whenever a server
 * group is found that does not conform to the expected scaling policies.
 *
 * What scaling policies are desired is determined by the condition of the
 * oldest server group in the cluster.
 */
class ClusterScalingPolicyTaggingAgent (
  val retrySupport: RetrySupport,
  val registry: Registry,
  val amazonClientProvider: AmazonClientProvider,
  val accounts: Collection<NetflixAmazonCredentials>,
  val entityTagger : EntityTagger,
  val objectMapper: ObjectMapper,
  val properties: ClusterScalingPolicyTaggingAgentProperties
) : RunnableAgent, CustomScheduledAgent {

  companion object {
    internal const val ENTITY_TAG_NAME = "spinnaker:scaling_policies"
    private const val REGISTRY_ID_PREFIX = "agents.clusterScalingPolicyTagging"

    internal val LIST_OF_MAPS = object : TypeReference<List<Map<String, Any>>>() {}
  }

  private val log = LoggerFactory.getLogger(ClusterScalingPolicyTaggingAgent::class.java)

  private val missingScalingPolicyCounterId = registry.createId("$REGISTRY_ID_PREFIX.serverGroupsMissingPolicies")
  private val updatedScalingPolicyCounterId = registry.createId("$REGISTRY_ID_PREFIX.clustersUpdated")

  override fun run() {
    log.info("Searching for cluster scaling policies")
    for (credentials in accounts) {
      for (region in credentials.regions) {
        val amazonAutoScaling = amazonClientProvider.getAutoScaling(credentials, region.name)

        // Assemble an index for the account/region of scaling policies...
        amazonAutoScaling.describeAutoScalingGroups().autoScalingGroups
          .let {
            val scalingPolicies = amazonAutoScaling.describePolicies().scalingPolicies
            val scalingPoliciesByServerGroupName = scalingPolicies.groupBy { it.autoScalingGroupName }
            val entityTagsByCluster = entityTagger.taggedEntities(
              "aws",
              credentials.accountId,
              "cluster",
              ENTITY_TAG_NAME,
              2000
            ).groupBy { it.entityRef.entityId }

            AccountRegionIndex(
              credentials = credentials,
              region = region,
              serverGroups = it,
              scalingPolicies = AccountRegionScalingPolicies(
                all = scalingPolicies,
                byServerGroupName = scalingPoliciesByServerGroupName,
                byCluster = getScalingPoliciesByCluster(it, scalingPoliciesByServerGroupName)
              ),
              clusterEntityTags = entityTagsByCluster
            )
          }
          // ...and then process it
          .run { forAccountRegion(this) }
      }
    }
  }

  /**
   * Reports on each server group that is missing cluster entity tags.
   *
   * If a cluster has server groups older than the old server group boundary,
   * the oldest server group will be assumed to have the "new" desired state
   * and will update the cluster entity tags to reflect its state.
   *
   * For all clusters that were not modified by old server groups' state,
   * the entity tags will be updated to reflect any cluster-level changes in
   * scaling policies (aggregate of scaling policies across all server groups).
   */
  internal fun forAccountRegion(accountRegion: AccountRegionIndex) {
    log.info("Scanning ${accountRegion.credentials.name}/${accountRegion.region.name} for scaling policies")

    val shouldUpdateClusterEntityTags = { it: Pair<String, AutoScalingGroup> ->
      val boundary = Instant.now().minusMillis(properties.oldServerGroupBoundaryMs)
      shouldUpdateClusterEntityTags(it.second, accountRegion.credentials, accountRegion.region, boundary)
    }

    // Report server groups that are missing cluster entity tags.
    // If the server group is old enough, update the cluster entity tags so
    // that they match the server group. We can assume that if the server
    // group is old enough and has deviated from what we have stored in
    // entity tags, this is the new, desired normal.
    val updatedClusters = accountRegion.serverGroups
      .map { Pair(Names.parseName(it.autoScalingGroupName).cluster, it) }
      .filter { accountRegion.clusterEntityTags.containsKey(it.first) }
      .filter(shouldUpdateClusterEntityTags)
      .let { clusterServerGroups ->
        // It's possible that multiple server groups in a cluster are old
        // enough to prompt updating the cluster's entity tags. In this case,
        // we'll take the oldest server group and use that as the assumed
        // desired state.
        clusterServerGroups
          .groupBy { it.first }
          .entries
          .map {
            it.value.sortedBy { it.second.createdTime }.first()
          }
      }
      .also {
        for ((cluster, serverGroup) in it) {
          accountRegion.scalingPolicies.byServerGroupName[serverGroup.autoScalingGroupName]?.also { scalingPolicies ->
            val clusterPolicies = scalingPolicies.toEntityTagValue()

            val logName = serverGroup.toLog(accountRegion.credentials, accountRegion.region)
            log.info("Updating cluster scaling policies from $logName with: $clusterPolicies")

            retry {
              entityTagger.tag(
                "aws",
                accountRegion.credentials.accountId,
                accountRegion.region.name,
                "spinnaker",
                ENTITY_TYPE_CLUSTER,
                cluster,
                ENTITY_TAG_NAME,
                clusterPolicies,
                System.currentTimeMillis()
              )
            }
          }
        }

        registry
          .counter(updatedScalingPolicyCounterId.withTag("process", "oldestServerGroup"))
          .increment(it.size.toLong())
      }
      .map { it.first }

    // Update the existing entity tags for each cluster to match the scaling
    // policies generated above. Clusters that were updated by older server
    // groups are excluded from this process to prevent stomping.
    // TODO rz - this pattern could potentially introduce flapping if a user
    // removes scaling policies on a super old server group, then changes
    // them on a newer server group, but never deletes the really old one.
    // I'm not sure how often this would actually happen in practice.
    accountRegion.scalingPolicies.byCluster
      .filterNot { updatedClusters.contains(it.key) }
      .also {
        for ((cluster, scalingPoliciesForCluster) in it) {
          val clusterPolicies = scalingPoliciesForCluster.toEntityTagValue()
          log.info("Tagging ${accountRegion.credentials.name}/${accountRegion.region.name}/$cluster " +
            "scaling policies: $clusterPolicies")
          retry {
            entityTagger.tag(
              "aws",
              accountRegion.credentials.accountId,
              accountRegion.region.name,
              "spinnaker",
              ENTITY_TYPE_CLUSTER,
              cluster,
              ENTITY_TAG_NAME,
              clusterPolicies,
              System.currentTimeMillis()
            )
          }
        }

        registry
          .counter(updatedScalingPolicyCounterId.withTag("process", "serverGroupsAggregate"))
          .increment(it.size.toLong())
      }
  }

  internal fun shouldUpdateClusterEntityTags(
    serverGroup: AutoScalingGroup,
    credentials: NetflixAmazonCredentials,
    region: AWSRegion,
    oldServerGroupBoundary: Instant
  ): Boolean {
    if (serverGroup.createdTime.toInstant().isBefore(oldServerGroupBoundary)) {
      log.warn(
        "Old server group is missing cluster scaling policies, " +
          "assuming system state is desired and updating entity tags: ${serverGroup.toLog(credentials, region)}"
      )
      registry.counter(missingScalingPolicyCounterId.withTag("serverGroupAge", "old")).increment()
      return true
    } else {
      log.info("Server group is missing scaling policies: ${serverGroup.toLog(credentials, region)}")
      registry.counter(missingScalingPolicyCounterId.withTag("serverGroupAge", "new")).increment()
    }

    return false
  }

  /**
   * Groups and de-duplicates all scaling policies by cluster.
   */
  internal fun getScalingPoliciesByCluster(
    serverGroups: List<AutoScalingGroup>,
    byServerGroupName: IndexedScalingPolicies
  ): IndexedScalingPolicies {
    val results = mutableMapOf<String, MutableList<ScalingPolicy>>()

    for (serverGroup in serverGroups) {
      val cluster = Names.parseName(serverGroup.autoScalingGroupName).cluster
      results[cluster] = results.getOrDefault(cluster, mutableListOf())
        .also { clusterPolicies ->
          for (policy in byServerGroupName.getOrDefault(serverGroup.autoScalingGroupName, emptyList())) {
            if (clusterPolicies.none { it.isEqual(policy) }) {
              clusterPolicies.add(policy)
            }
          }
        }
    }

    return results.filter { it.value.isNotEmpty() }
  }

  override fun getPollIntervalMillis(): Long = TimeUnit.SECONDS.toMillis(10)
  override fun getTimeoutMillis(): Long = TimeUnit.SECONDS.toMillis(30)
  override fun getAgentType(): String = ClusterScalingPolicyTaggingAgent::class.java.simpleName
  override fun getProviderName(): String = AwsProvider.PROVIDER_NAME

  internal fun List<ScalingPolicy>.toEntityTagValue(): Map<String, Any> =
    mapOf("value" to objectMapper.convertValue(map { it.toGenericMap(objectMapper) }, LIST_OF_MAPS))


  private fun retry(fn: () -> Unit) {
    retrySupport.retry(fn, 3, 500, false)
  }
}

internal typealias IndexedScalingPolicies = Map<String, List<ScalingPolicy>>

internal data class AccountRegionIndex(
  val credentials: NetflixAmazonCredentials,
  val region: AWSRegion,
  val serverGroups: List<AutoScalingGroup>,
  val scalingPolicies: AccountRegionScalingPolicies,
  val clusterEntityTags: Map<String, List<EntityTags>>
)

internal data class AccountRegionScalingPolicies(
  val all: List<ScalingPolicy>,
  val byServerGroupName: IndexedScalingPolicies,
  val byCluster: IndexedScalingPolicies
)

internal fun AutoScalingGroup.toLog(credentials: NetflixAmazonCredentials, region: AWSRegion) =
  "${credentials.name}/${region.name}/$autoScalingGroupName"

/**
 * This isn't exactly scientific, but it's better than just comparing num of scaling policies.
 */
internal fun ScalingPolicy.isEqual(scalingPolicy: ScalingPolicy): Boolean {
  return policyType == scalingPolicy.policyType &&
    adjustmentType == scalingPolicy.adjustmentType &&
    minAdjustmentStep == scalingPolicy.minAdjustmentStep &&
    minAdjustmentMagnitude == scalingPolicy.minAdjustmentMagnitude &&
    scalingAdjustment == scalingPolicy.scalingAdjustment &&
    cooldown == scalingPolicy.cooldown &&
    stepAdjustments.all { a -> scalingPolicy.stepAdjustments.any { b -> a == b } } &&
    metricAggregationType == scalingPolicy.metricAggregationType &&
    alarms.size == scalingPolicy.alarms.size &&
    targetTrackingConfiguration == scalingPolicy.targetTrackingConfiguration
}

internal fun ScalingPolicy.toGenericMap(objectMapper: ObjectMapper): Map<String, Any?> =
  objectMapper.convertValue<MutableMap<String, Any?>>(
    this, object : TypeReference<MutableMap<String, Any?>>() {}
  ).apply {
    remove("autoScalingGroupName")
    remove("policyName")
    remove("policyARN")
    set("alarms", alarms.map { mapOf<String, Any?>() })
  }

/**
 * This is kinda sad, but Entity Tags are designed to be a map, so if we're storing multiple values, the root-level
 * object cannot be a list... so we're just embedding it in a simple map.
 */
internal fun List<ScalingPolicy>.toEntityTagValue(objectMapper: ObjectMapper): Map<String, Any> =
  mapOf(
    "value" to objectMapper.convertValue(
      map { it.toGenericMap(objectMapper) },
      ClusterScalingPolicyTaggingAgent.LIST_OF_MAPS
    )
  )
