package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import org.springframework.stereotype.Component

import java.lang.annotation.Annotation

/**
 * Dcos declaration as a {@link CloudProvider}.
 */
@Component
class DcosCloudProvider implements CloudProvider {
  static final String ID = Keys.PROVIDER
  final String id = ID
  final String displayName = "Dcos"
  final Class<Annotation> operationAnnotationType = DcosOperation
}
