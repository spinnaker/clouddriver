package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import lombok.Data;

@Data
abstract class AbstractECSDescription extends AbstractAmazonCredentialsDescription {
  String application;
  String region;
  String stack;
  String freeFormDetails;
}
