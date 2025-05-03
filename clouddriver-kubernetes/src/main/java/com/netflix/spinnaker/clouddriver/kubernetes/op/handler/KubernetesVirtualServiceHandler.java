package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class KubernetesVirtualServiceHandler extends KubernetesHandler {
  @Override
  public int deployPriority() {
    return DeployPriority.MOUNTABLE_DATA_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.VIRTUAL_SERVICE;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.CONFIGS;
  }

  @Override
  public Manifest.Status status(KubernetesManifest manifest) {
    return Manifest.Status.defaultStatus();
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }
}
