package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DcosInstanceProvider implements InstanceProvider<DcosInstance> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosInstanceProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  final String cloudProvider = DcosCloudProvider.ID

  @Override
  DcosInstance getInstance(String account, String region, String name) {

    // TODO there should only be a single instance, right? Kubernetes uses getAllMatchingKeyPattern for some reason.
    CacheData instanceData = cacheView.get(Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, region, name))
    if (!instanceData) {
      return null
    }

    return objectMapper.convertValue(instanceData.attributes.instance, DcosInstance)
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null
  }
}
