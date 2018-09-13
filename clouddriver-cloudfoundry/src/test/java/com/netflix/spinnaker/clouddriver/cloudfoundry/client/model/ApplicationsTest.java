/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Applications;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ApplicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class ApplicationsTest {
  @Test
  void errorHandling() {
    CloudFoundryClient client = new CloudFoundryClient("pws", "api.run.pivotal.io",
      "baduser", "badpassword");

    assertThatThrownBy(() -> client.getApplications().all())
      .isInstanceOf(CloudFoundryApiException.class);
  }

  @Test
  void dontScaleApplicationIfInputsAreNullOrZero() {
    ApplicationService applicationService = mock(ApplicationService.class);

    Applications apps = new Applications("pws", applicationService, null);

    apps.scaleApplication("id", null, null, null);
    apps.scaleApplication("id", 0, 0, 0);

    verify(applicationService, never()).scaleApplication(any(), any());
  }
}
