/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesInstance
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesInstanceProvider implements InstanceProvider<KubernetesInstance> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesInstanceProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  final String cloudProvider = KubernetesCloudProvider.ID

  @Override
  KubernetesInstance getInstance(String account, String namespace, String name) {
    Set<CacheData> instances = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, namespace, name))
    if (!instances || instances.size() == 0) {
      return null
    }

    if (instances.size() > 1) {
      throw new IllegalStateException("Multiple kubernetes pods with name $name in namespace $namespace exist.")
    }

    CacheData instanceData = (CacheData) instances.toArray()[0]

    if (!instanceData) {
      return null
    }

    def loadBalancers = instanceData.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
      Keys.parse(it).name
    }

    KubernetesInstance instance = objectMapper.convertValue(instanceData.attributes.instance, KubernetesInstance)
    instance.loadBalancers = loadBalancers

    return instance
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null
  }
}
