/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.events;

import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusDeployHandler;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class TitusJobSubmitted extends SagaEvent {

  @Nonnull private final TitusDeployDescription description;
  @Nullable private final TitusDeployHandler.Front50Application front50App;
  @Nonnull private final String nextServerGroupName;
  @Nonnull private final Map<String, String> serverGroupNameByRegion;
  @Nonnull private final String jobUri;
  @Nullable private final TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult;

  public TitusJobSubmitted(
      @NotNull String sagaName,
      @NotNull String sagaId,
      @Nonnull TitusDeployDescription description,
      @Nullable TitusDeployHandler.Front50Application front50App,
      @Nonnull String nextServerGroupName,
      @Nonnull Map<String, String> serverGroupNameByRegion,
      @Nonnull String jobUri,
      @Nullable TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult) {
    super(sagaName, sagaId);
    this.description = description;
    this.front50App = front50App;
    this.nextServerGroupName = nextServerGroupName;
    this.serverGroupNameByRegion = serverGroupNameByRegion;
    this.jobUri = jobUri;
    this.targetGroupLookupResult = targetGroupLookupResult;
  }
}
