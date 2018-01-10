package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.ImageProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/images")
public class ImageController {

  @Autowired
  List<ImageProvider> imageProviders;

  @RequestMapping(value = "/{provider}/{imageId}", method = RequestMethod.GET)
  Artifact getImage(@PathVariable String provider, @PathVariable String imageId) {

    List<ImageProvider> imageProviderList = imageProviders.stream()
        .filter(imageProvider -> imageProvider.getCloudProvider().equals(provider))
        .collect(Collectors.toList());

    if (imageProviderList.isEmpty()) {
      throw new UnsupportedOperationException("ImageProvider for provider " + provider + " not found.");
    } else if (imageProviderList.size() > 1) {
      throw new UnsupportedOperationException("Found multiple ImageProviders for provider " + provider + ". Multiple ImageProviders for a single provider are not supported.");
    } else {
      return imageProviderList.get(0).getImageById(imageId).orElseThrow(() -> new NotFoundException("Image not found (id: " + imageId + ")"));
    }
  }
}
