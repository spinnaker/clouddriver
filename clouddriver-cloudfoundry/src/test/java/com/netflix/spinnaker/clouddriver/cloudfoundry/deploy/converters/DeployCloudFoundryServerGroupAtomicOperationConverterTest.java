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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.PackageArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.*;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class DeployCloudFoundryServerGroupAtomicOperationConverterTest {
  private static CloudFoundryCredentials createCredentials(String name) {
    CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
    {
      when(cloudFoundryClient.getOrganizations().findByName(any()))
        .thenAnswer((Answer<Optional<CloudFoundryOrganization>>) invocation -> {
          Object[] args = invocation.getArguments();
          return Optional.of(CloudFoundryOrganization.builder()
            .id(args[0].toString() + "ID").name(args[0].toString()).build());
        });

      when(cloudFoundryClient.getSpaces().findByName(any(), any())).thenAnswer((Answer<CloudFoundrySpace>) invocation -> {
        Object[] args = invocation.getArguments();
        return CloudFoundrySpace.builder().id(args[1].toString() + "ID").name(args[1].toString())
          .organization(CloudFoundryOrganization.builder()
            .id(args[0].toString()).name(args[0].toString().replace("ID", "")).build()).build();
      });
    }

    return new CloudFoundryCredentials(name, "", "", "", "", "", "") {
      public CloudFoundryClient getClient() {
        return cloudFoundryClient;
      }
    };
  }

  private List<String> accounts = List.of("test", "sourceAccount", "sourceAccount1", "sourceAccount2", "destinationAccount");

  private final ArtifactCredentialsRepository artifactCredentialsRepository = new ArtifactCredentialsRepository(
    Collections.singletonList(
      accounts.map(account -> new ArtifactCredentialsFromString(
        account,
        List.of("a").asJava(),
        "applications: [{instances: 42}]"
      )).asJava()
    )
  );

  private final AccountCredentialsRepository accountCredentialsRepository = new MapBackedAccountCredentialsRepository();

  {
    accounts.toStream().forEach(account -> accountCredentialsRepository.update(account, createCredentials(account)));
  }

  private final AccountCredentialsProvider accountCredentialsProvider =
    new DefaultAccountCredentialsProvider(accountCredentialsRepository);

  private final DeployCloudFoundryServerGroupAtomicOperationConverter converter =
    new DeployCloudFoundryServerGroupAtomicOperationConverter(null, artifactCredentialsRepository, null);

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  void convertDescriptionWitSourceSet() {
    final Map input = HashMap.of(
      "credentials", "destinationAccount",
      "region", "org > space",
      "startApplication", "true",
      "source", HashMap.of(
        "account", "sourceAccount",
        "asgName", "serverGroupName1",
        "region", "org > space"
      ).toJavaMap(),
      "manifest", HashMap.of(
        "type", "artifact",
        "account", "test",
        "reference", "ref1"
      ).toJavaMap()
    ).toJavaMap();

    final DeployCloudFoundryServerGroupDescription result = converter.convertDescription(input);

    CloudFoundryCredentials sourceCredentials = converter.getCredentialsObject("sourceAccount");
    assertThat(result.getAccountName()).isEqualTo("destinationAccount");
    assertThat(result.getArtifactCredentials())
      .isEqualToComparingFieldByFieldRecursively(new PackageArtifactCredentials(sourceCredentials.getClient()));
    assertThat(result.getSpace()).isEqualToComparingFieldByFieldRecursively(
      CloudFoundrySpace.builder().id("spaceID").name("space").organization(
        CloudFoundryOrganization.builder().id("orgID").name("org").build()).build());
    assertThat(result.isStartApplication()).isTrue();
    assertThat(result.getApplicationAttributes()).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(42)
        .setMemory("1024")
        .setDiskQuota("1024")
        .setBuildpacks(Collections.emptyList())
    );
  }

  @Test
  void convertDescriptionWithDestinationSet() {
    final Map input = HashMap.of(
      "credentials", "destinationAccount",
      "region", "org > space",
      "destination", HashMap.of(
        "account", "sourceAccount1",
        "region", "org1 > space1"
      ).toJavaMap(),
      "source", HashMap.of(
        "account", "sourceAccount2",
        "asgName", "serverGroupName1",
        "region", "org > space"
      ).toJavaMap(),
      "manifest", HashMap.of(
        "type", "artifact",
        "account", "test",
        "reference", "ref1"
      ).toJavaMap()
    ).toJavaMap();

    final DeployCloudFoundryServerGroupDescription result = converter.convertDescription(input);

    CloudFoundryCredentials sourceCredentials = converter.getCredentialsObject("sourceAccount2");
    assertThat(result.getAccountName()).isEqualTo("sourceAccount1");
    assertThat(result.getArtifactCredentials())
      .isEqualToComparingFieldByFieldRecursively(new PackageArtifactCredentials(sourceCredentials.getClient()));
    assertThat(result.getSpace()).isEqualToComparingFieldByFieldRecursively(
      CloudFoundrySpace.builder().id("space1ID").name("space1").organization(
        CloudFoundryOrganization.builder().id("org1ID").name("org1").build()).build());
    assertThat(result.getApplicationAttributes()).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(42)
        .setMemory("1024")
        .setDiskQuota("1024")
        .setBuildpacks(Collections.emptyList())
    );
  }

  @Test
  void convertManifestMapToApplicationAttributes() {
    final Map input = HashMap.of(
      "applications", List.of(
        HashMap.of(
          "instances", 7,
          "memory", "1G",
          "disk_quota", "2048M",
          "health-check-type", "http",
          "health-check-http-endpoint", "/health",
          "buildpacks", List.of(
            "buildpack1",
            "buildpack2"
          ).asJava(),
          "services", List.of(
            "service1"
          ).asJava(),
          "routes", List.of(
            HashMap.of(
              "route", "www.example.com/foo"
            ).toJavaMap()
          ).asJava(),
          "env", HashMap.of(
            "token", "ASDF"
          ).toJavaMap()
        ).toJavaMap()
      ).asJava()
    ).toJavaMap();

    assertThat(converter.convertManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(7)
        .setMemory("1G")
        .setDiskQuota("2048M")
        .setHealthCheckType("http")
        .setHealthCheckHttpEndpoint("/health")
        .setBuildpacks(List.of("buildpack1", "buildpack2").asJava())
        .setServices(List.of("service1").asJava())
        .setRoutes(List.of(
          "www.example.com/foo"
        ).asJava())
        .setEnv(HashMap.of(
          "token", "ASDF"
        ).toJavaMap())
    );
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingDeprecatedBuildpackAttr() {
    final Map input = HashMap.of(
      "applications", List.of(
        HashMap.of(
          "buildpack", "buildpack1"
        ).toJavaMap()
      ).asJava()
    ).toJavaMap();

    assertThat(converter.convertManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(1)
        .setMemory("1024")
        .setDiskQuota("1024")
        .setBuildpacks(List.of("buildpack1").asJava())
    );
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingDeprecatedBuildpackAttrBlankStringValue() {
    final Map input = HashMap.of(
      "applications", List.of(
        HashMap.of(
          "buildpack", ""
        ).toJavaMap()
      ).asJava()
    ).toJavaMap();

    assertThat(converter.convertManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(1)
        .setMemory("1024")
        .setDiskQuota("1024")
        .setBuildpacks(Collections.emptyList())
    );
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingWithNoBuildpacks() {
    final Map input = HashMap.of(
      "applications", List.of(
        Collections.EMPTY_MAP
      ).asJava()
    ).toJavaMap();

    assertThat(converter.convertManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
        .setInstances(1)
        .setMemory("1024")
        .setDiskQuota("1024")
        .setBuildpacks(Collections.emptyList())
    );
  }
}
