package com.netflix.spinnaker.clouddriver.tencent.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.model.Image

class TencentImage implements Image {
  String name
  String region
  String type
  String createdTime
  String imageId
  String osPlatform
  List<Map<String, Object>> snapshotSet

  @Override
  @JsonIgnore
  String getId() {
    return imageId
  }
}
