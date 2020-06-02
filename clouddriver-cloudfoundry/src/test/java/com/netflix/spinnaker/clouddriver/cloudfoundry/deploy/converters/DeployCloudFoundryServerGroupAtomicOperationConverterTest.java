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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class DeployCloudFoundryServerGroupAtomicOperationConverterTest {

  private static CloudFoundryCredentials createCredentials(String name) {
    CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
    CacheRepository cacheRepository = mock(CacheRepository.class);
    {
      when(cloudFoundryClient.getOrganizations().findByName(any()))
          .thenAnswer(
              (Answer<Optional<CloudFoundryOrganization>>)
                  invocation -> {
                    Object[] args = invocation.getArguments();
                    return Optional.of(
                        CloudFoundryOrganization.builder()
                            .id(args[0].toString() + "-guid")
                            .name(args[0].toString())
                            .build());
                  });

      when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
          .thenReturn(
              Optional.of(
                  CloudFoundrySpace.builder()
                      .id("space-guid")
                      .name("space")
                      .organization(
                          CloudFoundryOrganization.builder().id("org-guid").name("org").build())
                      .build()));

      when(cloudFoundryClient.getApplications().findServerGroupId(any(), any()))
          .thenReturn("servergroup-id");
    }

    return new CloudFoundryCredentials(
        name, "", "", "", "", "", "", false, 500, 16, cacheRepository) {
      public CloudFoundryClient getClient() {
        return cloudFoundryClient;
      }
    };
  }

  private List<String> accounts =
      List.of("test", "sourceAccount", "sourceAccount1", "sourceAccount2", "destinationAccount");

  private final ArtifactCredentialsRepository artifactCredentialsRepository =
      new ArtifactCredentialsRepository(
          Collections.singletonList(
              accounts
                  .map(
                      account ->
                          new ArtifactCredentialsFromString(
                              account, List.of("test").asJava(), "applications: [{instances: 42}]"))
                  .asJava()));

  private final AccountCredentialsRepository accountCredentialsRepository =
      new MapBackedAccountCredentialsRepository();

  {
    accounts
        .toStream()
        .forEach(
            account -> accountCredentialsRepository.update(account, createCredentials(account)));
  }

  private final AccountCredentialsProvider accountCredentialsProvider =
      new DefaultAccountCredentialsProvider(accountCredentialsRepository);

  private final DeployCloudFoundryServerGroupAtomicOperationConverter converter =
      new DeployCloudFoundryServerGroupAtomicOperationConverter(
          null, artifactCredentialsRepository);

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  void convertManifestMapToApplicationAttributes() {
    final Map input =
        HashMap.of(
                "applications",
                List.of(
                        HashMap.of(
                                "instances",
                                7,
                                "memory",
                                "1G",
                                "disk_quota",
                                "2048M",
                                "health-check-type",
                                "http",
                                "health-check-http-endpoint",
                                "/health",
                                "buildpacks",
                                List.of("buildpack1", "buildpack2").asJava(),
                                "services",
                                List.of("service1").asJava(),
                                "routes",
                                List.of(HashMap.of("route", "www.example.com/foo").toJavaMap())
                                    .asJava(),
                                "env",
                                HashMap.of("token", "ASDF").toJavaMap())
                            .toJavaMap())
                    .asJava())
            .toJavaMap();

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(7)
                .setMemory("1G")
                .setDiskQuota("2048M")
                .setHealthCheckType("http")
                .setHealthCheckHttpEndpoint("/health")
                .setBuildpacks(List.of("buildpack1", "buildpack2").asJava())
                .setServices(List.of("service1").asJava())
                .setRoutes(List.of("www.example.com/foo").asJava())
                .setEnv(HashMap.of("token", "ASDF").toJavaMap()));
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingDeprecatedBuildpackAttr() {
    final Map input =
        HashMap.of(
                "applications", List.of(HashMap.of("buildpack", "buildpack1").toJavaMap()).asJava())
            .toJavaMap();

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(1)
                .setMemory("1024")
                .setDiskQuota("1024")
                .setBuildpacks(List.of("buildpack1").asJava()));
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingDeprecatedBuildpackAttrBlankStringValue() {
    final Map input =
        HashMap.of("applications", List.of(HashMap.of("buildpack", "").toJavaMap()).asJava())
            .toJavaMap();

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(1)
                .setMemory("1024")
                .setDiskQuota("1024")
                .setBuildpacks(Collections.emptyList()));
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingWithNoBuildpacks() {
    final Map input =
        HashMap.of("applications", List.of(Collections.EMPTY_MAP).asJava()).toJavaMap();

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(1)
                .setMemory("1024")
                .setDiskQuota("1024")
                .setBuildpacks(Collections.emptyList()));
  }

  @Test
  void convertDescriptionTest() {
    Map<String, Object> description =
        ImmutableMap.of(
            "applicationArtifact",
                ImmutableMap.of(
                    "artifactAccount",
                    "destinationAccount",
                    "type",
                    "cloudfoundry/app",
                    "name",
                    "server-group-name",
                    "location",
                    "cf-region"),
            "credentials", "test",
            "manifest",
                ImmutableList.of(
                    ImmutableMap.of("applications", ImmutableList.of(ImmutableMap.of()))));

    DeployCloudFoundryServerGroupDescription result = converter.convertDescription(description);

    assertThat(result.getArtifactCredentials()).isNotNull();
    assertThat(result.getArtifactCredentials().getName()).isEqualTo("cloudfoundry");
    assertThat(result.getApplicationArtifact()).isNotNull();
    assertThat(result.getApplicationArtifact().getName()).isEqualTo("server-group-name");
    assertThat(result.getApplicationArtifact().getArtifactAccount())
        .isEqualTo("destinationAccount");
    assertThat(result.getApplicationArtifact().getUuid()).isEqualTo("servergroup-id");
  }
}
