package com.netflix.spinnaker.clouddriver.dcos.model

class DockerImage {
  private static final String IMAGE_NAME_SEPARATOR = ":"
  private static final String DEFAULT_REGISTRY = "docker.io"

  String imageRegistry
  String imageName
  String imageVersion

  String getImageId() {
    return [imageRegistry, imageName, imageVersion].join(IMAGE_NAME_SEPARATOR)
  }

  static DockerImage resolveImage(String image) {
    String[] imageNameParts = image.split(IMAGE_NAME_SEPARATOR)

    if (imageNameParts.size() == 2) {
      return new DockerImage(
        imageRegistry: DEFAULT_REGISTRY,
        imageName: imageNameParts[0],
        imageVersion: imageNameParts[1]
      )
    } else if (imageNameParts.size() == 3) {
      return new DockerImage(
        imageRegistry: imageNameParts[0],
        imageName: imageNameParts[1],
        imageVersion: imageNameParts[2]
      )
    } else {
      throw new IllegalArgumentException("Invalid docker image id specified: ${image}")
    }
  }
}
