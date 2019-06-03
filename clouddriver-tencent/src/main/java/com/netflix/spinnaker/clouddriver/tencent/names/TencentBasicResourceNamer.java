package com.netflix.spinnaker.clouddriver.tencent.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentBasicResource;
import com.netflix.spinnaker.moniker.Moniker;
import org.springframework.stereotype.Component;

@Component
public class TencentBasicResourceNamer implements NamingStrategy<TencentBasicResource> {
  @Override
  public String getName() {
    return "tencentAnnotations";
  }

  public void applyMoniker(TencentBasicResource tencentBasicResource, Moniker moniker) {}

  @Override
  public Moniker deriveMoniker(TencentBasicResource tencentBasicResource) {
    String name = tencentBasicResource.getMonikerName();
    Names parsed = Names.parseName(name);

    Moniker moniker =
        Moniker.builder()
            .app(parsed.getApp())
            .cluster(parsed.getCluster())
            .detail(parsed.getDetail())
            .stack(parsed.getStack())
            .detail(parsed.getDetail())
            .sequence(parsed.getSequence())
            .build();

    return moniker;
  }
}
