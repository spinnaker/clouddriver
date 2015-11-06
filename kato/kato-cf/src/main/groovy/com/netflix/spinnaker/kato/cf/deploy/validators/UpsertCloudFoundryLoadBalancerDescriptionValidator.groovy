package com.netflix.spinnaker.kato.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
/**
 * @author Greg Turnquist
 */
@CloudFoundryOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("upsertCloudFoundryLoadBalancerDescriptionValidator")
class UpsertCloudFoundryLoadBalancerDescriptionValidator extends DescriptionValidator<UpsertCloudFoundryLoadBalancerDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, UpsertCloudFoundryLoadBalancerDescription description, Errors errors) {
    def helper = new StandardCfAttributeValidator("upsertCloudFoundryLoadBalancerDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateNotEmpty(description.loadBalancerName, "loadBalancerName.empty")
//    helper.validateZone(description.zone)

  }
}
