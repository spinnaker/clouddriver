/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryServiceKeyDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import io.vavr.collection.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeleteCloudFoundryServiceKeyAtomicOperationConverterTest {
  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  private final CacheRepository cacheRepository = mock(CacheRepository.class);

  private CloudFoundryOrganization cloudFoundryOrganization =
      CloudFoundryOrganization.builder().id("org-guid").name("org").build();

  private CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .id("space-guid")
          .name("space")
          .organization(cloudFoundryOrganization)
          .build();

  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
        .thenReturn(Optional.of(cloudFoundryOrganization));
    when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
        .thenReturn(Optional.of(cloudFoundrySpace));
  }

  private final CloudFoundryCredentials cloudFoundryCredentials =
      new CloudFoundryCredentials(
          "my-account", "", "", "", "", "", "", false, 500, 16, cacheRepository, null) {
        public CloudFoundryClient getClient() {
          return cloudFoundryClient;
        }
      };

  private final AccountCredentialsRepository accountCredentialsRepository =
      new MapBackedAccountCredentialsRepository();
  private final String accountName = "my-account";

  {
    accountCredentialsRepository.update(accountName, cloudFoundryCredentials);
  }

  private final AccountCredentialsProvider accountCredentialsProvider =
      new DefaultAccountCredentialsProvider(accountCredentialsRepository);

  @Test
  void convertDescriptionSucceeds() {
    DeleteCloudFoundryServiceKeyAtomicOperationConverter converter =
        new DeleteCloudFoundryServiceKeyAtomicOperationConverter();
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());

    String serviceKeyName = "service-key-name";
    String serviceInstanceName = "service-instance-name";
    String region = "org > space";
    Map input =
        HashMap.of(
                "credentials", accountName,
                "region", region,
                "serviceInstanceName", serviceInstanceName,
                "serviceKeyName", serviceKeyName)
            .toJavaMap();

    DeleteCloudFoundryServiceKeyDescription expectedResult =
        (DeleteCloudFoundryServiceKeyDescription)
            new DeleteCloudFoundryServiceKeyDescription()
                .setServiceKeyName(serviceKeyName)
                .setServiceInstanceName(serviceInstanceName)
                .setSpace(cloudFoundrySpace)
                .setRegion(region)
                .setClient(cloudFoundryClient)
                .setCredentials(cloudFoundryCredentials);

    DeleteCloudFoundryServiceKeyDescription result = converter.convertDescription(input);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }
}
