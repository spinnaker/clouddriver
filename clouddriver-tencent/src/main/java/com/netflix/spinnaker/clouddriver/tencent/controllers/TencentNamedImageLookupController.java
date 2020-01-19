package com.netflix.spinnaker.clouddriver.tencent.controllers;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES;

import com.google.common.collect.Iterables;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/tencent/images")
public class TencentNamedImageLookupController {
  @RequestMapping(value = "/{account}/{region}/{imageId:.+}", method = RequestMethod.GET)
  public List<NamedImage> getByImgId(
      @PathVariable final String account,
      @PathVariable final String region,
      @PathVariable final String imageId) {
    CacheData cache = cacheView.get(IMAGES.getNs(), Keys.getImageKey(imageId, account, region));
    if (cache == null) {
      throw new NotFoundException(imageId + " not found in " + account + "/" + region);
    }
    Collection<String> namedImageKeys = cache.getRelationships().get(NAMED_IMAGES.getNs());
    if (!CollectionUtils.isEmpty(namedImageKeys)) {
      throw new NotFoundException(
          "Name not found on image " + imageId + " in " + account + "/" + region);
    }
    return render(null, Arrays.asList(cache), null, region);
  }

  @RequestMapping(value = "/find", method = RequestMethod.GET)
  public List<NamedImage> list(LookupOptions lookupOptions, HttpServletRequest request) {
    log.info("TencentNamedImageLookupController lookupOptions = {}", lookupOptions);
    validateLookupOptions(lookupOptions);
    String glob = StringUtils.isEmpty(lookupOptions.getQ()) ? null : lookupOptions.getQ().trim();
    boolean isImgId = Pattern.matches(IMG_GLOB_PATTERN, glob);

    // Wrap in '*' if there are no glob-style characters in the query string
    if (!isImgId
        && !glob.contains("*")
        && !glob.contains("?")
        && !glob.contains("[")
        && !glob.contains("\\")) {
      glob = "*" + glob + "*";
    }

    log.info("TencentNamedImageLookupController glob is {}", glob);

    final String account = lookupOptions.getAccount();
    String namedImageSearch =
        Keys.getNamedImageKey(glob, !StringUtils.isEmpty(account) ? account : "*");
    final String region = lookupOptions.getRegion();
    String imageSearch =
        Keys.getImageKey(
            glob,
            !StringUtils.isEmpty(account) ? account : "*",
            !StringUtils.isEmpty(region) ? region : "*");

    log.info("TencentNamedImageLookupController namedImageSearch is {}", namedImageSearch);
    log.info("TencentNamedImageLookupController imageSearch is {}", imageSearch);
    Collection<String> namedImageIdentifiers =
        !isImgId
            ? cacheView.filterIdentifiers(NAMED_IMAGES.getNs(), namedImageSearch)
            : new ArrayList();
    Collection<String> imageIdentifiers =
        namedImageIdentifiers.isEmpty()
            ? cacheView.filterIdentifiers(IMAGES.getNs(), imageSearch)
            : new ArrayList();

    namedImageIdentifiers =
        namedImageIdentifiers.stream()
            .limit(Math.min(MAX_SEARCH_RESULTS, namedImageIdentifiers.size()))
            .collect(Collectors.toList());
    Collection<CacheData> matchesByName =
        cacheView.getAll(
            NAMED_IMAGES.getNs(),
            namedImageIdentifiers,
            RelationshipCacheFilter.include(IMAGES.getNs()));
    Collection<CacheData> matchesByImageId = cacheView.getAll(IMAGES.getNs(), imageIdentifiers);
    log.info("TencentNamedImageLookupController matchesByImageId = {}", matchesByImageId);
    log.info(
        "TencentNamedImageLookupController cache get all = {}",
        cacheView.getAll(NAMED_IMAGES.getNs()));
    log.info("TencentNamedImageLookupController namedImageIdentifiers = {}", namedImageIdentifiers);
    return render(matchesByName, matchesByImageId, lookupOptions.getQ(), lookupOptions.getRegion());
  }

  public void validateLookupOptions(LookupOptions lookupOptions) {
    if (lookupOptions.getQ() == null || lookupOptions.getQ().length() < MIN_NAME_FILTER) {
      throw new InvalidRequestException(EXCEPTION_REASON);
    }

    String glob = StringUtils.isEmpty(lookupOptions.getQ()) ? null : lookupOptions.getQ().trim();
    boolean isImgId = Pattern.matches(IMG_GLOB_PATTERN, glob);
    if (glob.equals("img") || (!isImgId && glob.startsWith("img-"))) {
      throw new InvalidRequestException(
          "Searches by Image Id must be an exact match (img-xxxxxxxx)");
    }
  }

  private List<NamedImage> render(
      Collection<CacheData> namedImages,
      Collection<CacheData> images,
      String requestedName,
      String requiredRegion) {
    Map<String, NamedImage> byImageName = new HashMap<String, NamedImage>();

    Collection<String> relations = new ArrayList<>();

    namedImages.stream()
        .forEach(
            it -> {
              Collection<String> relationships =
                  it.getRelationships().getOrDefault(IMAGES.getNs(), null);
              relations.addAll(relationships);
            });

    cacheView.getAll(IMAGES.getNs(), relations);

    namedImages.stream()
        .forEach(
            it -> {
              Map<String, String> keyParts = Keys.parse(it.getId());
              String imageName = keyParts.get("imageName");
              byImageName.putIfAbsent(
                  imageName,
                  new NamedImage() {
                    {
                      setImageName(imageName);
                    }
                  });
              NamedImage namedImage = byImageName.get(imageName);
              log.info(
                  "TencentNamedImageLookupController namedImages it.attributes {}",
                  it.getAttributes());
              namedImage.getAttributes().putAll(it.getAttributes());
              namedImage.getAttributes().remove("name", imageName);
              namedImage.getAccounts().add(keyParts.get("account"));

              if (!it.getRelationships().isEmpty()
                  && it.getRelationships().containsKey(IMAGES.getNs())) {
                for (String imageKey : it.getRelationships().get(IMAGES.getNs())) {
                  Map<String, String> imageParts = Keys.parse(imageKey);
                  namedImage.imgIds.putIfAbsent(imageParts.get("region"), new HashSet<String>());
                  namedImage.imgIds.get(imageParts.get("region")).add(imageParts.get("imageId"));
                }
              }
            });

    images.stream()
        .forEach(
            it -> {
              Map<String, String> keyParts = Keys.parse(it.getId());
              Map<String, String> namedImageKeyParts =
                  Keys.parse(Iterables.get(it.getRelationships().get(NAMED_IMAGES.getNs()), 0));
              String imageName = namedImageKeyParts.get("imageName");
              byImageName.putIfAbsent(
                  imageName,
                  new NamedImage() {
                    {
                      setImageName(imageName);
                    }
                  });
              NamedImage namedImage = byImageName.get(imageName);
              Map<String, Object> image = (Map<String, Object>) it.getAttributes().get("image");
              namedImage.getAttributes().put("osPlatform", image.get("osPlatform"));
              namedImage.getAttributes().put("imageName", imageName);
              namedImage.getAttributes().put("region", image.get("region"));
              namedImage.getAttributes().put("type", image.get("type"));
              namedImage.getAttributes().put("snapshotSet", it.getAttributes().get("snapshotSet"));
              namedImage.getAttributes().put("createdTime", image.get("createdTime"));
              namedImage.getAccounts().add(namedImageKeyParts.get("account"));
              namedImage.getImgIds().putIfAbsent(keyParts.get("region"), new HashSet<>());
              namedImage.getImgIds().get(keyParts.get("region")).add(keyParts.get("imageId"));
            });

    List<NamedImage> results =
        byImageName.values().stream()
            .filter(
                it -> {
                  return !StringUtils.isEmpty(requiredRegion)
                      ? it.getImgIds().containsKey(requiredRegion)
                      : true;
                })
            .collect(Collectors.toList());
    return results;
  }

  private List<NamedImage> render(
      Collection<CacheData> namedImages, Collection<CacheData> images, String requestedName) {
    return render(namedImages, images, requestedName, null);
  }

  private List<NamedImage> render(Collection<CacheData> namedImages, Collection<CacheData> images) {
    return render(namedImages, images, null, null);
  }

  private static final int MAX_SEARCH_RESULTS = 1000;
  private static final int MIN_NAME_FILTER = 3;
  private static final String EXCEPTION_REASON =
      "Minimum of " + MIN_NAME_FILTER + " characters required to filter namedImages";
  private final String IMG_GLOB_PATTERN = "^img-([\\w+0-9]{8})$";

  @Autowired private Cache cacheView;

  @Data
  private static class NamedImage {
    String imageName;
    Map<String, Object> attributes = new HashMap<>();
    Set<String> accounts = new HashSet<>();
    Map<String, HashSet<String>> imgIds = new HashMap<>();
  }

  @Data
  public static class LookupOptions {
    private String q;
    private String account;
    private String region;
  }
}
