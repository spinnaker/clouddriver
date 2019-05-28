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
package com.netflix.spinnaker.clouddriver.saga.repository;

import com.google.common.annotations.Beta;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Beta
public interface SagaRepository {
  @Nonnull
  ListResult<Saga> list(@Nonnull ListStateCriteria criteria);

  @Nullable
  Saga get(@Nonnull String sagaId);

  @Nullable
  Saga get(@Nonnull String sagaId, Instant version);

  @Nonnull
  Saga upsert(@Nonnull Saga saga);

  @Nonnull
  Saga upsert(@Nonnull SagaStep sagaStep);

  //  long reap(Collection<String> sagaIds);
  //  long reap(long maxRecords);
  //  long reap(Instant olderThan);

  interface ListResult<T> {
    @Nonnull
    List<T> getResult();

    @Nullable
    String getNextToken();
  }
}
