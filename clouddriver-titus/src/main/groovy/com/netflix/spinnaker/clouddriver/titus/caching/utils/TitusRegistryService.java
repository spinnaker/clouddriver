package com.netflix.spinnaker.clouddriver.titus.caching.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import retrofit.http.GET;
import retrofit.http.Path;

public interface TitusRegistryService {

  @GET("/v2/{applicationName}/manifests/{version}")
  BuildInfo getBuildInformation(
      @Path(value = "applicationName", encode = false) String applicationName,
      @Path("version") String version);

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  class BuildInfo {
    String schemaVersion;
    List<v1> history;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class v1 {
      String v1Compatibility;
    }
  }
}
