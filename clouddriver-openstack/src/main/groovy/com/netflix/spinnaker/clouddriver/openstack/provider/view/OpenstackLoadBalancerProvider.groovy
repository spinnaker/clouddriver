/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

@Component
class OpenstackLoadBalancerProvider implements LoadBalancerProvider<OpenstackLoadBalancer> {

    final Cache cacheView
    final ObjectMapper objectMapper

    @Autowired
    OpenstackLoadBalancerProvider(final Cache cacheView, final ObjectMapper objectMapper) {
        this.cacheView = cacheView
        this.objectMapper = objectMapper
    }

    @Override
    Set<OpenstackLoadBalancer> getApplicationLoadBalancers(String application) {
        String pattern = Keys.getLoadBalancerKey("*", "*", "${application}*")
        Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern)
        Collection<CacheData> data = cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.include(SERVER_GROUPS.ns, INSTANCES.ns))
        !data ? Sets.newHashSet() : data.collect(this.&fromCacheData)
    }

    OpenstackLoadBalancer fromCacheData(CacheData cacheData) {
        objectMapper.convertValue(cacheData.attributes, OpenstackLoadBalancer)
    }

}
