/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Organizations;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

public class CloudFoundryCredentialsTest {

  private final CacheRepository cacheRepository = mock(CacheRepository.class);
  private final Organizations organizations = mock(Organizations.class);
  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();

  @Test
  void emptyLocationFilterShouldConvertToEmptyList() {
    CloudFoundryCredentials credentials =
        new CloudFoundryCredentials(
            "test",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            500,
            cacheRepository,
            null,
            ForkJoinPool.commonPool(),
            emptyMap());

    assertThat(credentials.getFilteredSpaces()).isEqualTo(emptyList());
  }

  @Test
  void singleOrgLocationFilterShouldConvert() {
    CloudFoundryCredentials credentials =
        new CloudFoundryCredentials(
            "test",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            500,
            cacheRepository,
            null,
            ForkJoinPool.commonPool(),
            emptyMap()) {
          public CloudFoundryClient getClient() {
            return cloudFoundryClient;
          }

          public CloudFoundryClient getCredentials() {
            return cloudFoundryClient;
          }
        };

    Map<String, Set<String>> locationFilter = ImmutableMap.of("org", emptySet());

    CloudFoundryOrganization organization =
        CloudFoundryOrganization.builder().id("org123").name("org").build();
    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space123")
            .name("space1")
            .organization(organization)
            .build();
    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space456")
            .name("space2")
            .organization(organization)
            .build();

    when(cloudFoundryClient.getSpaces().findAllBySpaceNamesAndOrgNames(isNull(), any()))
        .thenReturn(List.of(space1, space2));
    List<CloudFoundrySpace> result = credentials.createFilteredSpaces(locationFilter);
    assertThat(result).isEqualTo(List.of(space1, space2));
  }

  @Test
  void singleOrgSingleSpaceLocationFilterShouldConvert() {
    CloudFoundryCredentials credentials =
        new CloudFoundryCredentials(
            "test",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            500,
            cacheRepository,
            null,
            ForkJoinPool.commonPool(),
            emptyMap()) {
          public CloudFoundryClient getClient() {
            return cloudFoundryClient;
          }

          public CloudFoundryClient getCredentials() {
            return cloudFoundryClient;
          }
        };

    Map<String, Set<String>> locationFilter = ImmutableMap.of("org", Set.of("space1"));

    CloudFoundryOrganization organization =
        CloudFoundryOrganization.builder().id("org123").name("org").build();
    CloudFoundrySpace space1 =
        CloudFoundrySpace.builder()
            .id("space123")
            .name("space1")
            .organization(organization)
            .build();
    CloudFoundrySpace space2 =
        CloudFoundrySpace.builder()
            .id("space456")
            .name("space2")
            .organization(organization)
            .build();

    when(cloudFoundryClient.getSpaces().findAllBySpaceNamesAndOrgNames(any(), any()))
        .thenReturn(List.of(space1, space2));
    List<CloudFoundrySpace> result = credentials.createFilteredSpaces(locationFilter);
    assertThat(result).isEqualTo(List.of(space1));
  }

  @Test
  void fakeOrgFakeSpaceLocationFilterShouldThrowError() {
    CloudFoundryCredentials credentials =
        new CloudFoundryCredentials(
            "test",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            500,
            cacheRepository,
            null,
            ForkJoinPool.commonPool(),
            emptyMap()) {
          public CloudFoundryClient getClient() {
            return cloudFoundryClient;
          }

          public CloudFoundryClient getCredentials() {
            return cloudFoundryClient;
          }
        };

    Map<String, Set<String>> locationFilter = ImmutableMap.of("org", Set.of("space1"));

    when(cloudFoundryClient.getSpaces().findAllBySpaceNamesAndOrgNames(any(), any()))
        .thenReturn(emptyList());
    Exception e =
        assertThrows(Exception.class, () -> credentials.createFilteredSpaces(locationFilter));
    assertThat(e)
        .hasMessageContaining(
            "The locationFilter had Orgs and/or Spaces but we found no spaces as a result. Spaces must not be null or empty when a locationFilter is included.");
  }

  @Test
  void fakeOrgLocationFilterShouldThrowError() {
    CloudFoundryCredentials credentials =
        new CloudFoundryCredentials(
            "test",
            "",
            "",
            "",
            "",
            "",
            "",
            false,
            500,
            cacheRepository,
            null,
            ForkJoinPool.commonPool(),
            emptyMap()) {
          public CloudFoundryClient getClient() {
            return cloudFoundryClient;
          }

          public CloudFoundryClient getCredentials() {
            return cloudFoundryClient;
          }
        };

    Map<String, Set<String>> locationFilter = ImmutableMap.of("org", emptySet());

    when(cloudFoundryClient.getSpaces().findAllBySpaceNamesAndOrgNames(any(), any()))
        .thenReturn(emptyList());
    Exception e =
        assertThrows(Exception.class, () -> credentials.createFilteredSpaces(locationFilter));
    assertThat(e)
        .hasMessageContaining(
            "Organization 'org' returned with no spaces and was provided as a filter. Organization must exist and have spaces.");
  }
}
