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

import static com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper.TargetGroupLookupResult;

import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.handlers.TitusDeployHandler;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class TitusDeployPrepared extends SagaEvent {

  @Nonnull private final TitusDeployDescription description;
  @Nullable private final TitusDeployHandler.Front50Application front50App;
  @Nonnull private final SubmitJobRequest submitJobRequest;
  @Nonnull private final String nextServerGroupName;
  @Nullable private final TargetGroupLookupResult targetGroupLookupResult;

  public TitusDeployPrepared(
      @NotNull String sagaName,
      @NotNull String sagaId,
      @Nonnull TitusDeployDescription description,
      @Nullable TitusDeployHandler.Front50Application front50App,
      @Nonnull SubmitJobRequest submitJobRequest,
      @Nonnull String nextServerGroupName,
      @Nullable TargetGroupLookupResult targetGroupLookupResult) {
    super(sagaName, sagaId);
    this.description = description;
    this.front50App = front50App;
    this.submitJobRequest = submitJobRequest;
    this.nextServerGroupName = nextServerGroupName;
    this.targetGroupLookupResult = targetGroupLookupResult;
  }
}
