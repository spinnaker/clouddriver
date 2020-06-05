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

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ShareCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.ShareCloudFoundryServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.Map;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.SHARE_SERVICE)
@Component
public class ShareCloudFoundryServiceAtomicOperationConverter
    extends AbstractCloudFoundryAtomicOperationConverter {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new ShareCloudFoundryServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public ShareCloudFoundryServiceDescription convertDescription(Map input) {
    ShareCloudFoundryServiceDescription converted =
        getObjectMapper().convertValue(input, ShareCloudFoundryServiceDescription.class);
    CloudFoundryCredentials credentials = getCredentialsObject(input.get("credentials").toString());
    converted.setCredentials(credentials);
    converted.setClient(getClient(input));
    return converted;
  }
}
