package com.netflix.spinnaker.clouddriver.tencent.deploy.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.AbstractTencentCredentialsDescription;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import java.util.Map;
import org.springframework.util.StringUtils;

public class TencentAtomicOperationConverterHelper {
  public static <T extends AbstractTencentCredentialsDescription> T convertDescription(
      Map input,
      AbstractAtomicOperationsCredentialsSupport credentialsSupport,
      Class<T> description) {
    input.putIfAbsent("accountName", input.get("credentials"));

    if (!StringUtils.isEmpty(input.get("accountName"))) {
      input.put(
          "credentials",
          credentialsSupport.getCredentialsObject((String) input.get("accountName")));
    }

    // Save these to re-assign after ObjectMapper does its work.
    Object credentials = input.remove("credentials");
    T converted =
        credentialsSupport
            .getObjectMapper()
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(input, description);

    // Re-assign the credentials.
    converted.setCredentials((TencentNamedAccountCredentials) credentials);
    return converted;
  }
}
