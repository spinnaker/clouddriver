package com.netflix.spinnaker.clouddriver.dcos.provider

import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.netflix.frigga.NameValidation
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.PathId

import static com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup.*

class DcosProviderUtils {

  public static final String GLOBAL_REGION = 'global'

  /**
   * Resolves a set of CacheData given a namespace and a key pattern. If a {@code relationshipFilter} is supplied, relationships for the returned data will be filtered using it.
   * @param cacheView The Cache from which CacheData for the relationships will be resolved.
   * @param namespace The cache namespace
   * @param pattern The key pattern to match against
   * @param relationshipFilter An optional CacheFilter for filtering relationships.
   * @return Set of CacheData
   */
  static Set<CacheData> getAllMatchingKeyPattern(Cache cacheView, String namespace, String pattern, CacheFilter relationshipFilter) {
    loadResults(cacheView, namespace, cacheView.filterIdentifiers(namespace, pattern), relationshipFilter)
  }

  /**
   * Resolves a set of CacheData given a namespace and a key pattern. No relationships will be filtered.
   * @param cacheView The Cache from which CacheData for the relationships will be resolved.
   * @param namespace The cache namespace
   * @param pattern The key pattern to match against
   * @return Set of CacheData
   */
  static Set<CacheData> getAllMatchingKeyPattern(Cache cacheView, String namespace, String pattern) {
    loadResults(cacheView, namespace, cacheView.filterIdentifiers(namespace, pattern), RelationshipCacheFilter.none())
  }

  private
  static Set<CacheData> loadResults(Cache cacheView, String namespace, Collection<String> identifiers, CacheFilter relationshipFilter) {
    cacheView.getAll(namespace, identifiers, relationshipFilter ? relationshipFilter : RelationshipCacheFilter.none())
  }

  /**
   * Resolves CacheData for the {@code relationship} keys that exist on the supplied {@source} CacheData. An optional relationship filter may be provided.
   * @param cacheView The Cache from which CacheData for the relationships will be resolved.
   * @param source The source from which the relationships will be resolved.
   * @param relationship The relationship to resolve on the the source CacheData.
   * @return Non-null but possibly empty collection of CacheData.
   */
  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship) {
    resolveRelationshipData(cacheView, source, relationship) { true }
  }

  /**
   * Resolves CacheData for the {@code relationship} keys that exist on the supplied {@source} CacheData. An optional relationship filter may be provided.
   * @param cacheView The Cache from which CacheData for the relationships will be resolved.
   * @param source The source from which the relationships will be resolved.
   * @param relationship The relationship to resolve on the the source CacheData.
   * @param relFilter An optional closure that can filter out relationships.
   * @return Non-null but possibly empty collection of CacheData.
   */
  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source?.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

  /**
   * Similar to {@link #resolveRelationshipData}, except resolves the relationships from a given collection of source CacheData.
   * @param cacheView The Cache from which CacheData for the relationships will be resolved.
   * @param sources The sources from which the relationships will be resolved.
   * @param relationship The relationship to resolve on the the source CacheData.
   * @param relFilter An optional CacheFilter that can filter out relationships.
   * @return Non-null but possibly empty collection of CacheData.
   */
  static Collection<CacheData> resolveRelationshipDataForCollection(Cache cacheView, Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Set<String> relationships = sources.findResults { it.relationships[relationship] ?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  static String combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }

  static boolean validateLoadBalancerId(PathId loadBalancerId, String account) {
    loadBalancerId.size() == 2 && loadBalancerId.first().get() == account
  }

  static boolean validateLoadBalancerId(String loadBalancerId, String account) {
    validateLoadBalancerId(PathId.parse(loadBalancerId), account)
  }

  static ImageDescription buildImageDescription(String image) {

    if (!image || image.isEmpty()) {
      return null
    }

    def sIndex = image.indexOf('/')
    def result = new ImageDescription()

    // No slash means we only provided a repository name & optional tag.
    if (sIndex < 0) {
      result.repository = image
    } else {
      def sPrefix = image.substring(0, sIndex)

      // Check if the content before the slash is a registry (either localhost, or a URL)
      if (sPrefix.startsWith('localhost') || sPrefix.contains('.')) {
        result.registry = sPrefix

        image = image.substring(sIndex + 1)
      }
    }

    def cIndex = image.indexOf(':')

    if (cIndex < 0) {
      result.repository = image
    } else {
      result.tag = image.substring(cIndex + 1)
      result.repository = image.subSequence(0, cIndex)
    }

    normalizeImageDescription(result)
    result
  }

  static Void normalizeImageDescription(ImageDescription image) {
    if (!image.registry) {
      image.registry = "hub.docker.com" // TODO configure or pull from docker registry account
    }

    if (!image.tag) {
      image.tag = "latest"
    }

    if (!image.repository) {
      throw new IllegalArgumentException("Image descriptions must provide a repository.")
    }
  }

  static <T> void registerDeserializer(ObjectMapper objectMapper, Class<T> clazz, JsonDeserializer<T> deserializer) {
    SimpleModule module = new SimpleModule()
    module.addDeserializer(clazz, deserializer)
    objectMapper.registerModule(module)
  }
}
