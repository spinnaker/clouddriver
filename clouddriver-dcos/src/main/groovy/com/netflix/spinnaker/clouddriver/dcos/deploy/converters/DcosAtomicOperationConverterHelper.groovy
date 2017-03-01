package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport

class DcosAtomicOperationConverterHelper {
  static <T> T convertDescription(Map input,
                                  AbstractAtomicOperationsCredentialsSupport credentialsSupport,
                                  Class targetDescriptionType) {
    def account = input.account as String
    def removedAccount = input.remove('credentials')
    account = account ?: removedAccount

    // Save these to re-assign after ObjectMapper does its work.
    def credentials = (DcosCredentials) credentialsSupport.getCredentialsObject(account as String)

    def converted = (AbstractDcosCredentialsDescription) credentialsSupport.objectMapper
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(input, targetDescriptionType)

    // Re-assign the credentials.
    converted.credentials = credentials
    if (removedAccount) {
      input.credentials = removedAccount
      converted.account = removedAccount
    }

    (T) converted
  }
}
