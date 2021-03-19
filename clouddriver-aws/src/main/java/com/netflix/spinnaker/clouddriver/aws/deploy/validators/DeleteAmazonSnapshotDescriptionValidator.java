/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteAmazonSnapshotDescription;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AmazonOperation(AtomicOperations.DELETE_SNAPSHOT)
@Component
public class DeleteAmazonSnapshotDescriptionValidator
    extends AmazonDescriptionValidationSupport<DeleteAmazonSnapshotDescription> {

  CredentialsRepository<NetflixAmazonCredentials> credentialsRepository;

  @Autowired
  public DeleteAmazonSnapshotDescriptionValidator(
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository) {
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  public void validate(
      List priorDescriptions,
      DeleteAmazonSnapshotDescription description,
      ValidationErrors errors) {
    String key = DeleteAmazonSnapshotDescription.class.getSimpleName();
    validateRegion(description, description.getRegion(), key, errors);

    if (description.getRegion().equals("") || description.getRegion() == null) {
      errors.rejectValue("region", "deleteAmazonSnapshotDescription.region.empty");
    }
    if (description.getSnapshotId().equals("") || description.getSnapshotId() == null) {
      errors.rejectValue("snapshotId", "deleteAmazonSnapshotDescription.snapshotId.empty");
    }
  }
}
