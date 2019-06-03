package com.netflix.spinnaker.clouddriver.tencent

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import org.springframework.stereotype.Component

import java.lang.annotation.Annotation

@Component
class TencentCloudProvider implements CloudProvider {
  public static final String ID = "tencent"
  final String id = ID
  final String displayName = "Tencent"
  final Class<Annotation> operationAnnotationType = TencentOperation
}
