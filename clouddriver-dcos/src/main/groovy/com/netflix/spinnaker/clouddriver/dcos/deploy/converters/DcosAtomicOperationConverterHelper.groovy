package com.netflix.spinnaker.clouddriver.dcos.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport

class DcosAtomicOperationConverterHelper {
  static <T> T convertDescription(Map input,
                                  AbstractAtomicOperationsCredentialsSupport credentialsSupport,
                                  Class targetDescriptionType) {
    String account = input.account
    def removedAccount = input.remove('credentials')
    account = account ?: removedAccount

    // Save these to re-assign after ObjectMapper does its work.
    def credentials = (DcosAccountCredentials) credentialsSupport.getCredentialsObject(account)

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

    // Extract the dcosCluster field if it's not already populated.
    if (!converted.dcosCluster) {
      converted.dcosCluster = input.dcosCluster ?: converted.region ? (converted.region as String).split(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR).first() : null
    }

    // Extract the group field if it's not already populated.
    if (!converted.group) {
      converted.group = input.group ?: converted.region ? (converted.region as String).split(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR).tail().join("/") : null
    }

    // Extract the region field if it's not already populated.
    if (!converted.region && converted.dcosCluster) {
      converted.region = converted.group ? "${converted.dcosCluster}/${converted.group}".toString() : converted.dcosCluster
    }

    (T) converted
  }
}
