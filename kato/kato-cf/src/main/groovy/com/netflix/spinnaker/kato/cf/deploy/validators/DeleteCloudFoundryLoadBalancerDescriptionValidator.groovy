package com.netflix.spinnaker.kato.cf.deploy.validators
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.deploy.description.DeleteCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors
/**
 * @author Greg Turnquist
 */
@CloudFoundryOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component("deleteCloudFoundryLoadBalancerDescriptionValidator")
class DeleteCloudFoundryLoadBalancerDescriptionValidator extends DescriptionValidator<DeleteCloudFoundryLoadBalancerDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, DeleteCloudFoundryLoadBalancerDescription description, Errors errors) {
    def helper = new StandardCfAttributeValidator("deleteCloudFoundryLoadBalancerDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateNotEmpty(description.loadBalancerName, "loadBalancerName.empty")
//    helper.validateZone(description.zone)

  }
}
