package com.netflix.spinnaker.clouddriver.tencent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.model.Image;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TencentImage implements Image {
  private String name;
  private String region;
  private String type;
  private String createdTime;
  private String imageId;
  private String osPlatform;
  private List<Map<String, Object>> snapshotSet;

  @Override
  @JsonIgnore
  public String getId() {
    return imageId;
  }
}
