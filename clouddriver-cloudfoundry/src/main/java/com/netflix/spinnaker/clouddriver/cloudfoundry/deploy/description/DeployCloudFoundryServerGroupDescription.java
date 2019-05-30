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
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DeployCloudFoundryServerGroupDescription
    extends AbstractCloudFoundryServerGroupDescription {
  private String accountName;
  private String application;
  private String stack;
  private String freeFormDetails;
  private CloudFoundrySpace space;
  private boolean startApplication;
  private Artifact applicationArtifact;
  private Artifact manifest;
  private String executionId;
  private Map<String, Object> trigger;

  @JsonIgnore private ArtifactCredentials artifactCredentials;

  @JsonIgnore private ApplicationAttributes applicationAttributes;

  @Data
  public static class ApplicationAttributes {
    private int instances;
    private String memory;
    private String diskQuota;

    @Nullable private String healthCheckType;

    @Nullable private String healthCheckHttpEndpoint;

    @Nullable private List<String> routes;

    @Nullable private List<String> buildpacks;

    @Nullable private Map<String, String> env;

    @Nullable private List<String> services;
  }
}
