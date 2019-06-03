package com.netflix.spinnaker.clouddriver.tencent.provider.agent

import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient
import com.netflix.spinnaker.clouddriver.tencent.model.TencentImage
import com.netflix.spinnaker.clouddriver.tencent.provider.view.MutableCacheData
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.NAMED_IMAGES

@Slf4j
@InheritConstructors
class TencentImageCachingAgent extends AbstractTencentCachingAgent {

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(IMAGES.ns)
  ] as Set

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info "start load image data"

    Map<String, Collection<CacheData>> cacheResults = [:]
    Map<String, Map<String, CacheData>> namespaceCache = [:].withDefault {
      namespace -> [:].withDefault { id -> new MutableCacheData(id as String) }
    }
    Map<String, Collection<String>> evictions = [:].withDefault {
      namespace -> []
    }

    CloudVirtualMachineClient cvmClient = new CloudVirtualMachineClient(
      credentials.credentials.secretId,
      credentials.credentials.secretKey,
      region
    )

    def result = cvmClient.getImages()

    result.each {
      def tencentImage = new TencentImage(
        region: this.region,
        name: it.imageName,
        imageId: it.imageId,
        type: it.imageType,
        osPlatform: it.platform,
        createdTime: it.createdTime
      )

      if (it.snapshotSet) {
        def snapshotSet = it.snapshotSet.collect {
          Map<String, Object> snapshot = objectMapper.convertValue it, ATTRIBUTES
          snapshot
        }
        tencentImage.snapshotSet = snapshotSet
      }

      def images = namespaceCache[IMAGES.ns]
      def namedImages = namespaceCache[NAMED_IMAGES.ns]
      def imageKey = Keys.getImageKey tencentImage.id, this.accountName, this.region
      def namedImageKey = Keys.getNamedImageKey tencentImage.name, this.accountName
      images[imageKey].attributes.image = tencentImage
      images[imageKey].attributes.snapshotSet = tencentImage.snapshotSet
      images[imageKey].relationships[NAMED_IMAGES.ns].add namedImageKey

      def originImageCache = providerCache.get(IMAGES.ns, imageKey)
      if (originImageCache) {
        def imageNames = originImageCache.relationships[NAMED_IMAGES.ns]
        if (imageNames && imageNames[0] != namedImageKey) {
          evictions[NAMED_IMAGES.ns].addAll(originImageCache.relationships[NAMED_IMAGES.ns])
        }
      }

      namedImages[namedImageKey].attributes.imageName = tencentImage.name
      namedImages[namedImageKey].attributes.type = tencentImage.type
      namedImages[namedImageKey].attributes.osPlatform = tencentImage.osPlatform
      namedImages[namedImageKey].attributes.snapshotSet = tencentImage.snapshotSet
      namedImages[namedImageKey].attributes.createdTime = tencentImage.createdTime
      namedImages[namedImageKey].relationships[IMAGES.ns].add imageKey
      null
    }

    namespaceCache.each { String namespace, Map<String, CacheData> cacheDataMap ->
      cacheResults[namespace] = cacheDataMap.values()
    }

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults, evictions)
    log.info 'finish loads image data.'
    log.info "Caching ${namespaceCache[IMAGES.ns].size()} items in $agentType"
    defaultCacheResult
  }
}
