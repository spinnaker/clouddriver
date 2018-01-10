package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;

import java.util.Optional;

public interface ImageProvider {
  Optional<Artifact> getImageById(String imageId);

  String getCloudProvider();
}
