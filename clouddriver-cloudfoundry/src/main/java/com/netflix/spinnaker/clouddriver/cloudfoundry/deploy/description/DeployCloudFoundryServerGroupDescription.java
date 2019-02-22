/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeployCloudFoundryServerGroupDescription extends AbstractCloudFoundryServerGroupDescription {
  private String accountName;
  private String application;
  private String stack;
  private String freeFormDetails;
  private CloudFoundrySpace space;

  @Nullable
  private Source source;

  @Nullable
  private Destination destination;

  @JsonIgnore
  private Artifact artifact;

  @JsonIgnore
  private ArtifactCredentials artifactCredentials;

  @JsonIgnore
  private ApplicationAttributes applicationAttributes;

  @Data
  public static class ApplicationAttributes {
    private int instances;
    private String memory;
    private String diskQuota;

    @Nullable
    private String healthCheckType;

    @Nullable
    private String healthCheckHttpEndpoint;

    @Nullable
    private List<String> routes;

    @Nullable
    private List<String> buildpacks;

    @Nullable
    private Map<String, String> env;

    @Nullable
    private List<String> services;
  }

  @Data
  public static class Source {
    String account;
    String region;
    String asgName;
  }

  @Data
  public static class Destination {
    String account;
    String region;
  }
}
