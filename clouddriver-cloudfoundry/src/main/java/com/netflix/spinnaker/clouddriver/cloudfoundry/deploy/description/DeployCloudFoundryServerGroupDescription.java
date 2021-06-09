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
import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Docker;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessRequest;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.RandomStringUtils;

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
  private List<Map<Object, Object>> manifest;
  private String executionId;
  private Map<String, Object> trigger;

  @JsonIgnore private ArtifactCredentials artifactCredentials;

  @JsonIgnore private ApplicationAttributes applicationAttributes;

  @JsonIgnore private Docker docker;

  @Data
  public static class ApplicationAttributes {
    private int instances;
    private String memory;
    private String diskQuota;

    @Nullable private String healthCheckType;

    @Nullable private String healthCheckHttpEndpoint;

    @Getter(AccessLevel.NONE)
    @Nullable
    private List<String> routes;

    @Nullable private List<String> buildpacks;

    @Nullable private Map<String, String> env;

    @Nullable private List<String> services;

    @Nullable private String stack;

    @Nullable private String command;

    private List<ProcessRequest> processes = Collections.emptyList();

    @Getter(AccessLevel.NONE)
    @Nullable
    private Boolean randomRoute;

    @Nullable
    public List<String> getRoutes() {
      if ((routes == null || routes.isEmpty()) && getRandomRoute()) {
        routes = Lists.newArrayList(RandomStringUtils.randomAlphabetic(5, 10));
      }
      return routes;
    }

    public boolean getRandomRoute() {
      return randomRoute != null && randomRoute;
    }
  }
}
