package com.netflix.spinnaker.clouddriver.tencent.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport

class TencentAtomicOperationConverterHelper {
  static <T> T convertDescription(
    Map input,
    AbstractAtomicOperationsCredentialsSupport credentialsSupport,
    Class<T> description
  ) {
    if (!input.accountName) {
      input.accountName = input.credentials
    }
    if (input.accountName) {
      input.credentials = credentialsSupport.getCredentialsObject(
        input.accountName as String)
    }
    // Save these to re-assign after ObjectMapper does its work.
    def credentials = input.remove("credentials")
    def converted = credentialsSupport.objectMapper
      .copy()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .convertValue(input, description)

    // Re-assign the credentials.
    converted.credentials = credentials
    converted
  }
}
