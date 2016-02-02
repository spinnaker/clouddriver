/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AzureLoadBalancerProvider implements LoadBalancerProvider<AzureLoadBalancer> {

  private final AzureCloudProvider azureCloudProvider
  private final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  AzureLoadBalancerProvider(AzureCloudProvider azureCloudProvider, Cache cacheView, ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  /**
   * Retrieves all load balancers, keyed on load balancer name
   *
   * @return loadBalancerName => set of load balancer objects
   */
  @Override
  Map<String, Set<AzureLoadBalancer>> getLoadBalancers() {
    Map<String, Set<AzureLoadBalancer>> partitionedLb = [:].withDefault { new HashSet<AzureLoadBalancer>() }
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, '*', '*', '*', '*', '*', '*')).each {
      lb -> partitionedLb[lb.account].add(lb)
    }

    partitionedLb
  }

  /**
   * Retrieves all load balancers for a specific account
   *
   * @param account
   * @return set of load balancer objects
   */
  @Override
  Set<AzureLoadBalancer> getLoadBalancers(String account) {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, '*', '*', '*', '*', '*', account))
  }

  /**
   * Gets the load balancers for a specified cluster in a specified account
   *
   * @param account
   * @param cluster
   * @return a set of load balancers
   */
  @Override
  Set<AzureLoadBalancer> getLoadBalancers(String account, String cluster) {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, '*', '*', '*', cluster, '*', account))
  }

  /**
   * Load balancer objects are identified by the composite key of their type, name, and region of existence. This method will return a set of load balancers of a specific type from a cluster that
   * exists within a specified account
   *
   * @param account
   * @param cluster
   * @param type
   * @return set of load balancers
   */
  @Override
  Set<AzureLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    def azureLoadBalancers = getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, '*', '*', cluster, '*', '*', account))

    azureLoadBalancers
  }

  /**
   * This method is a more specific version of {@link #getLoadBalancers(java.lang.String, java.lang.String, java.lang.String)}, filtering down to the load balancer name. This method will return a
   * set of load balancers, as they may exist in different regions
   *
   * @param account
   * @param cluster
   * @param type
   * @param loadBalancerName
   * @return set of regions
   */
  @Override
  Set<AzureLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, loadBalancerName, '*', '*', cluster, '*', account))
  }

  /**
   * Similar to {@link #getLoadBalancer(java.lang.String, java.lang.String, java.lang.String, java.lang.String)}, except with the specificity of region
   *
   * @param account
   * @param cluster
   * @param type
   * @param loadBalancerName
   * @param region
   * @return a specific load balancer
   */
  @Override
  AzureLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, loadBalancerName, '*', '*', cluster, region, account)).first()
  }

  /**
   * Returns all load balancers related to an application based on one of the following criteria:
   *   - the load balancer name follows the Frigga naming conventions for load balancers (i.e., the load balancer name starts with the application name, followed by a hyphen)
   *   - the load balancer is used by a server group in the application
   * @param application the name of the application
   * @return a collection of load balancers with all attributes populated and a minimal amount of data
   *         for each server group: its name, region, and *only* the instances attached to the load balancers described above.
   *         The instances will have a minimal amount of data, as well: name, zone, and health related to any load balancers
   */
  @Override
  Set<AzureLoadBalancer> getApplicationLoadBalancers(String application) {
    getAllMatchingKeyPattern(Keys.getLoadBalancerKey(azureCloudProvider, '*', '*', application, '*', '*', '*'))
  }

  Set<AzureLoadBalancer> getAllMatchingKeyPattern(String pattern) {
    loadResults(cacheView.filterIdentifiers(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, pattern))
  }

  Set<AzureLoadBalancer> loadResults(Collection<String> identifiers) {
    def transform = this.&fromCacheData
    def data = cacheView.getAll(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    def transformed = data.collect(transform)

    return transformed
  }

  AzureLoadBalancer fromCacheData(CacheData cacheData) {
    AzureLoadBalancerDescription loadBalancerDescription = objectMapper.convertValue(cacheData.attributes['loadbalancer'], AzureLoadBalancerDescription)
    def parts = Keys.parse(azureCloudProvider, cacheData.id)

    new AzureLoadBalancer(
      account: parts.account?: "none",
      name: loadBalancerDescription.loadBalancerName,
      region: loadBalancerDescription.region,
      vnet: loadBalancerDescription.vnet?: "none"
    )
  }

  AzureLoadBalancerDescription getLoadBalancerDescription(String account, String appName, String region, String loadBalancerName) {
    def pattern = Keys.getLoadBalancerKey(azureCloudProvider, loadBalancerName, '*', appName, '*', region, account)
    def identifiers = cacheView.filterIdentifiers(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, pattern)
    def data = cacheView.getAll(Keys.Namespace.AZURE_LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none())
    CacheData cacheData = data? data.first() : null

    cacheData? objectMapper.convertValue(cacheData.attributes['loadbalancer'], AzureLoadBalancerDescription) : null
  }

}
