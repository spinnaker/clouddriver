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

import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class TitusDeployCreated extends SagaEvent {
  @Nonnull private final TitusDeployDescription description;
  @Nonnull private final List priorOutputs;
  @Nonnull private final String inputChecksum;

  public TitusDeployCreated(
      @NotNull String sagaName,
      @NotNull String sagaId,
      @Nonnull TitusDeployDescription description,
      @Nonnull List priorOutputs,
      @Nonnull String inputChecksum) {
    super(sagaName, sagaId);
    this.description = description;
    this.priorOutputs = priorOutputs;
    this.inputChecksum = inputChecksum;
  }
}
