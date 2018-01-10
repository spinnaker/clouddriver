

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;

import java.util.Optional;

public class NoopImageProvider implements ImageProvider {

  @Override
  public Optional<Artifact> getImageById(String imageId) {
    return Optional.empty();
  }

  @Override
  public String getCloudProvider() {
    return "none";
  }
}
