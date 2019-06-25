package com.netflix.spinnaker.clouddriver.google.deploy.converters;

import com.netflix.spinnaker.clouddriver.google.GoogleOperation;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.description.StatefullyUpdateBootImageDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.StatefullyUpdateBootImageAtomicOperation;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@GoogleOperation(AtomicOperations.STATEFULLY_UPDATE_BOOT_IMAGE)
@Component
public class StatefullyUpdateBootImageOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final GoogleClusterProvider clusterProvider;
  private final GoogleComputeApiFactory computeApiFactory;
  private final GoogleConfigurationProperties googleConfigurationProperties;

  @Autowired
  public StatefullyUpdateBootImageOperationConverter(
      GoogleClusterProvider clusterProvider,
      GoogleComputeApiFactory computeApiFactory,
      GoogleConfigurationProperties googleConfigurationProperties) {
    this.clusterProvider = clusterProvider;
    this.computeApiFactory = computeApiFactory;
    this.googleConfigurationProperties = googleConfigurationProperties;
  }

  @Override
  public StatefullyUpdateBootImageAtomicOperation convertOperation(Map input) {
    return new StatefullyUpdateBootImageAtomicOperation(
        clusterProvider,
        computeApiFactory,
        googleConfigurationProperties,
        convertDescription(input));
  }

  @Override
  public StatefullyUpdateBootImageDescription convertDescription(Map input) {
    return GoogleAtomicOperationConverterHelper.convertDescription(
        input, this, StatefullyUpdateBootImageDescription.class);
  }
}
