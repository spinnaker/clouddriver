/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.Map;

@AmazonOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("createAmazonLoadBalancerDescriptionValidator")
class CreateAmazonLoadBalancerDescriptionValidator extends AmazonDescriptionValidationSupport<UpsertAmazonLoadBalancerDescription> {

  @Override
  public void validate(List priorDescriptions, UpsertAmazonLoadBalancerDescription description, Errors errors) {
    // Common fields to validate
    if (description.name == null && description.clusterName == null) {
      errors.rejectValue("clusterName", "createAmazonLoadBalancerDescription.missing.name.or.clusterName");
    }
    if (description.subnetType == null && description.availabilityZones == null) {
      errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.missing.subnetType.or.availabilityZones");
    }

    for (Map.Entry<String, List<String>> entry : description.availabilityZones.entrySet()) {
      String region = entry.getKey();
      List<String> azs = entry.getValue();

      AmazonCredentials.AWSRegion acctRegion = description.getCredentials().getRegions().stream().filter(r -> r.getName().equals(region)).findFirst().orElse(null);
      if (acctRegion == null) {
        errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.region.not.configured");
      }
      if (description.subnetType == null && azs == null) {
        errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.missing.subnetType.or.availabilityZones");
        break;
      }
      if (description.subnetType == null && acctRegion != null && !acctRegion.getAvailabilityZones().containsAll(azs)) {
        errors.rejectValue("availabilityZones", "createAmazonLoadBalancerDescription.zone.not.configured");
      }
    }

    switch (description.loadBalancerType) {
      case ELB_CLASSIC:
        UpsertAmazonLoadBalancerClassicDescription classicDescription = (UpsertAmazonLoadBalancerClassicDescription) description;
        if (classicDescription.listeners == null || classicDescription.listeners.size() == 0) {
          errors.rejectValue("listeners", "createAmazonLoadBalancerDescription.listeners.empty");
        }
        break;
      case ALB:
        UpsertAmazonLoadBalancerV2Description albDescription = (UpsertAmazonLoadBalancerV2Description) description;
        if (albDescription.targetGroups == null || albDescription.targetGroups.size() == 0) {
          errors.rejectValue("targetGroups", "createAmazonLoadBalancerDescription.targetGroups.empty");
        }
        for (UpsertAmazonLoadBalancerV2Description.TargetGroup targetGroup : albDescription.targetGroups) {
          if (targetGroup.name == null || targetGroup.name.isEmpty()) {
            errors.rejectValue("targetGroups", "createAmazonLoadBalancerDescription.targetGroups.name.missing");
          }
          if (targetGroup.protocol == null) {
            errors.rejectValue("targetGroups", "createAmazonLoadBalancerDescription.targetGroups.protocol.missing");
          }
          if (targetGroup.port == null) {
            errors.rejectValue("targetGroups", "createAmazonLoadBalancerDescription.targetGroups.port.missing");
          }
        }
        break;
    }
    }
}
