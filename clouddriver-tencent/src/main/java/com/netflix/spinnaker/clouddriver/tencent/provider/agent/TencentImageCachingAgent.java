package com.netflix.spinnaker.clouddriver.tencent.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.NAMED_IMAGES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.client.CloudVirtualMachineClient;
import com.netflix.spinnaker.clouddriver.tencent.model.NamespaceCache;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentImage;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.cvm.v20170312.models.Image;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.util.CollectionUtils;

@Slf4j
public class TencentImageCachingAgent extends AbstractTencentCachingAgent {
  @Override
  public CacheResult loadData(final ProviderCache providerCache) {
    // log.info("start load image data");

    final Map<String, Collection<CacheData>> cacheResults =
        new HashMap<String, Collection<CacheData>>();
    final NamespaceCache namespaceCache = new NamespaceCache();

    final Map<String, Collection<String>> evictions = new HashMap<>();

    CloudVirtualMachineClient cvmClient =
        new CloudVirtualMachineClient(
            getCredentials().getCredentials().getSecretId(),
            getCredentials().getCredentials().getSecretKey(),
            getRegion());

    List<Image> result = cvmClient.getImages();
    log.info(
        "TencentImageCachingAgent loadData imageNames = {}",
        result.stream().map(it -> it.getImageName()).collect(Collectors.joining(",")));
    log.info(
        "TencentImageCachingAgent loadData imageIds = {}",
        result.stream().map(it -> it.getImageId()).collect(Collectors.joining(",")));

    result.stream()
        .forEach(
            it -> {
              TencentImage tencentImage =
                  TencentImage.builder()
                      .name(it.getImageName())
                      .imageId(it.getImageId())
                      .type(it.getImageType())
                      .osPlatform(it.getPlatform())
                      .createdTime(it.getCreatedTime())
                      .build();

              if (!ArrayUtils.isEmpty(it.getSnapshotSet())) {
                List<Map<String, Object>> snapshotSet =
                    Arrays.stream(it.getSnapshotSet())
                        .map(
                            that -> {
                              Map<String, Object> snapshot =
                                  getObjectMapper().convertValue(that, getATTRIBUTES());
                              return snapshot;
                            })
                        .collect(Collectors.toList());
                tencentImage.setSnapshotSet(snapshotSet);
              }

              Map<String, CacheData> images = namespaceCache.get(IMAGES.ns);

              Map<String, CacheData> namedImages = namespaceCache.get(NAMED_IMAGES.ns);
              String imageKey =
                  Keys.getImageKey(
                      tencentImage.getId(),
                      TencentImageCachingAgent.this.getAccountName(),
                      TencentImageCachingAgent.this.getRegion());
              String namedImageKey =
                  Keys.getNamedImageKey(tencentImage.getName(), this.getAccountName());
              images.get(imageKey).getAttributes().put("image", tencentImage);
              images
                  .get(imageKey)
                  .getAttributes()
                  .put("snapshotSet", tencentImage.getSnapshotSet());
              images.get(imageKey).getRelationships().get(NAMED_IMAGES.ns).add(namedImageKey);

              CacheData originImageCache = providerCache.get(IMAGES.ns, imageKey);
              if (originImageCache != null) {
                Collection<String> imageNames =
                    originImageCache.getRelationships().get(NAMED_IMAGES.ns);
                if (!CollectionUtils.isEmpty(imageNames)
                    && !imageNames.iterator().next().equals(namedImageKey)) {
                  evictions.putIfAbsent(NAMED_IMAGES.ns, new ArrayList<String>());
                  evictions
                      .get(NAMED_IMAGES.ns)
                      .addAll(originImageCache.getRelationships().get(NAMED_IMAGES.ns));
                }
              }

              namedImages
                  .get(namedImageKey)
                  .getAttributes()
                  .put("imageName", tencentImage.getName());
              namedImages.get(namedImageKey).getAttributes().put("type", tencentImage.getType());
              namedImages
                  .get(namedImageKey)
                  .getAttributes()
                  .put("osPlatform", tencentImage.getOsPlatform());
              namedImages
                  .get(namedImageKey)
                  .getAttributes()
                  .put("snapshotSet", tencentImage.getSnapshotSet());
              namedImages
                  .get(namedImageKey)
                  .getAttributes()
                  .put("createdTime", tencentImage.getCreatedTime());
              namedImages.get(namedImageKey).getRelationships().get(IMAGES.ns).add(imageKey);
            });

    namespaceCache.forEach(
        (namespace, cacheDataMap) -> {
          cacheResults.put(namespace, cacheDataMap.values());
        });

    CacheResult defaultCacheResult = new DefaultCacheResult(cacheResults, evictions);
    log.info("TencentImageCachingAgent finish loads image data.");
    log.info("Caching " + namespaceCache.get(IMAGES.ns).size() + " items in " + getAgentType());
    return defaultCacheResult;
  }

  public TencentImageCachingAgent(
      TencentNamedAccountCredentials credentials, ObjectMapper objectMapper, String region) {
    super(credentials, objectMapper, region);
  }

  public final Set<AgentDataType> getProvidedDataTypes() {
    return providedDataTypes;
  }

  private final Set<AgentDataType> providedDataTypes =
      new HashSet<AgentDataType>() {
        {
          AUTHORITATIVE.forType(IMAGES.ns);
        }
      };
}
