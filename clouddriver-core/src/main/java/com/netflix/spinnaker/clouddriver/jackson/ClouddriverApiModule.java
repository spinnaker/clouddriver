/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.clouddriver.jackson.mixins.*;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.SecurityGroup;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.model.securitygroups.Rule;

public class ClouddriverApiModule extends SimpleModule {

  public ClouddriverApiModule() {
    super("Clouddriver API");
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.setMixInAnnotations(SecurityGroup.class, SecurityGroupMixin.class);
    context.setMixInAnnotations(Rule.class, RuleMixin.class);
    context.setMixInAnnotations(Cluster.class, ClusterMixin.class);
    context.setMixInAnnotations(ServerGroup.class, ServerGroupMixin.class);
    context.setMixInAnnotations(ServerGroup.ImageSummary.class, ImageSummaryMixin.class);
    context.setMixInAnnotations(ServerGroup.ImagesSummary.class, ImagesSummaryMixin.class);
    context.setMixInAnnotations(
        LoadBalancerProvider.Item.class, LoadBalancerProviderItemMixin.class);
    context.setMixInAnnotations(
        LoadBalancerProvider.ByAccount.class, LoadBalancerProviderByAccountMixin.class);
    context.setMixInAnnotations(
        LoadBalancerProvider.ByRegion.class, LoadBalancerProviderByRegionMixin.class);
  }
}
