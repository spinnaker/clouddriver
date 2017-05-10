package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors

abstract class AbstractDcosDescriptionValidatorSupport<T extends AbstractDcosCredentialsDescription> extends DescriptionValidator<T> {

  private final AccountCredentialsProvider accountCredentialsProvider
  protected final String descriptionName

  AbstractDcosDescriptionValidatorSupport(AccountCredentialsProvider accountCredentialsProvider, String descriptionName) {
    this.accountCredentialsProvider = accountCredentialsProvider
    this.descriptionName = descriptionName
  }

  @Override
  void validate(List priorDescriptions, T description, Errors errors) {
    if (!description.dcosCluster || description.dcosCluster.trim().empty) {
      errors.rejectValue "dcosCluster", "${descriptionName}.dcosCluster.empty"
    }

    if (!description.credentials) {
      errors.rejectValue "credentials", "${descriptionName}.credentials.empty"
    } else {
      if (!(description.credentials instanceof DcosAccountCredentials)) {
        errors.rejectValue("credentials", "${descriptionName}.credentials.invalid")
      } else if (description.dcosCluster?.trim() && !description.credentials.getCredentials().getCredentialsByCluster(description.dcosCluster)) {
        errors.rejectValue("dcosCluster", "${descriptionName}.dcosCluster.invalid")
      }
    }
  }

  AccountCredentials getAccountCredentials(String accountName) {
    accountCredentialsProvider.getCredentials(accountName)
  }
}
