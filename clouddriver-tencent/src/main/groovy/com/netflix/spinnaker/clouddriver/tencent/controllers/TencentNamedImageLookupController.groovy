package com.netflix.spinnaker.clouddriver.tencent.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES

@Slf4j
@RestController
@RequestMapping("/tencent/images")
class TencentNamedImageLookupController {
  private static final int MAX_SEARCH_RESULTS = 1000
  private static final int MIN_NAME_FILTER = 3
  private static final String EXCEPTION_REASON = 'Minimum of ' + MIN_NAME_FILTER + ' characters required to filter namedImages'

  private final IMG_GLOB_PATTERN = /^img-([a-f0-9]{8})$/

  @Autowired
  private final Cache cacheView

  @RequestMapping(value = '/{account}/{region}/{imageId:.+}', method = RequestMethod.GET)
  List<NamedImage> getByImgId(@PathVariable String account, @PathVariable String region, @PathVariable String imageId) {
    CacheData cache = cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region))
    if (cache == null) {
      throw new NotFoundException("${imageId} not found in ${account}/${region}")
    }
    Collection<String> namedImageKeys = cache.relationships[NAMED_IMAGES.ns]
    if (!namedImageKeys) {
      throw new NotFoundException("Name not found on image ${imageId} in ${account}/${region}")
    }
    render(null, [cache], null, region)
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<NamedImage> list(LookupOptions lookupOptions, HttpServletRequest request) {
    validateLookupOptions lookupOptions

    String glob = lookupOptions.q?.trim()
    def isImgId = glob ==~ IMG_GLOB_PATTERN

    // Wrap in '*' if there are no glob-style characters in the query string
    if (!isImgId &&
      !glob.contains('*') &&
      !glob.contains('?') &&
      !glob.contains('[') &&
      !glob.contains('\\')) {
      glob = "*${glob}*"
    }

    def namedImageSearch = Keys.getNamedImageKey glob, lookupOptions.account ?: '*'
    def imageSearch = Keys.getImageKey(
      glob, lookupOptions.account ?: '*', lookupOptions.region ?: '*')

    Collection<String> namedImageIdentifiers = !isImgId ?
      cacheView.filterIdentifiers(NAMED_IMAGES.ns, namedImageSearch) : []
    Collection<String> imageIdentifiers = namedImageIdentifiers.isEmpty() ?
      cacheView.filterIdentifiers(IMAGES.ns, imageSearch) : []

    namedImageIdentifiers = (namedImageIdentifiers as List).subList(
      0, Math.min(MAX_SEARCH_RESULTS, namedImageIdentifiers.size()))
    Collection<CacheData> matchesByName = cacheView.getAll(
      NAMED_IMAGES.ns, namedImageIdentifiers, RelationshipCacheFilter.include(IMAGES.ns))
    Collection<CacheData> matchesByImageId = cacheView.getAll(IMAGES.ns, imageIdentifiers)

    return render(matchesByName, matchesByImageId, lookupOptions.q, lookupOptions.region)
  }

  def validateLookupOptions(LookupOptions lookupOptions) {
    if (lookupOptions.q == null || lookupOptions.q.length() < MIN_NAME_FILTER) {
      throw new InvalidRequestException(EXCEPTION_REASON)
    }

    String glob = lookupOptions.q?.trim()
    def isImgId = glob ==~ IMG_GLOB_PATTERN
    if (glob == "img" || (!isImgId && glob.startsWith("img-"))) {
      throw new InvalidRequestException("Searches by Image Id must be an exact match (img-xxxxxxxx)")
    }
  }

  private List<NamedImage> render(
    Collection<CacheData> namedImages, Collection<CacheData> images,
    String requestedName = null, String requiredRegion = null
  ) {
    Map<String, NamedImage> byImageName = [:].withDefault { new NamedImage(imageName: it) }

    cacheView.getAll(IMAGES.ns, namedImages.collect {
      (it.relationships[IMAGES.ns] ?: []).collect {
        it
      }
    }.flatten() as Collection<String>)

    namedImages.each {
      Map<String, String> keyParts = Keys.parse it.id
      NamedImage namedImage = byImageName[keyParts.imageName]
      namedImage.attributes.putAll it.attributes - [name: keyParts.imageName]
      namedImage.accounts.add keyParts.account

      for (String imageKey : it.relationships[IMAGES.ns] ?: []) {
        Map<String, String> imageParts = Keys.parse imageKey
        namedImage.imgIds[imageParts.region].add imageParts.imageId
      }
    }

    images.each {
      Map<String, String> keyParts = Keys.parse it.id
      Map<String, String> namedImageKeyParts = Keys.parse it.relationships[NAMED_IMAGES.ns][0]
      NamedImage namedImage = byImageName[namedImageKeyParts.imageName]
      def image = it.attributes.image
      namedImage.attributes.osPlatform = image["osPlatform"]
      namedImage.attributes.type = image["type"]
      namedImage.attributes.snapshotSet =  it.attributes.snapshotSet
      namedImage.attributes.createdTime = image["createdTime"]
      namedImage.accounts.add namedImageKeyParts.account
      namedImage.imgIds[keyParts.region].add keyParts.imageId
    }

    List<NamedImage> results = byImageName.values().findAll {
      requiredRegion ? it.imgIds.containsKey(requiredRegion) : true
    }

    results
  }

  private static class NamedImage {
    String imageName
    Map<String, Object> attributes = [:]
    Set<String> accounts = []
    Map<String, Collection<String>> imgIds = [:].withDefault { new HashSet<String>() }
  }


  static class LookupOptions {
    String q
    String account
    String region
  }
}
