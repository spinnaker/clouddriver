package com.netflix.spinnaker.clouddriver.tencent;

import com.netflix.spinnaker.clouddriver.core.CloudProvider;
import org.springframework.stereotype.Component;

@Component
public class TencentCloudProvider implements CloudProvider {
  public final String getId() {
    return id;
  }

  public final String getDisplayName() {
    return displayName;
  }

  public final Class<TencentOperation> getOperationAnnotationType() {
    return operationAnnotationType;
  }

  public static final String ID = "tencent";
  private final String id = ID;
  private final String displayName = "Tencent";
  private final Class<TencentOperation> operationAnnotationType = TencentOperation.class;
}
