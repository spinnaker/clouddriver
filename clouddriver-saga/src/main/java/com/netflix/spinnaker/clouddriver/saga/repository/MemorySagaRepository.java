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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemorySagaRepository implements SagaRepository {

  private static final Logger log = LoggerFactory.getLogger(MemorySagaRepository.class);

  private final Cache<String, Saga> sagas;

  public MemorySagaRepository() {
    this(10_000, Duration.ofDays(3));
  }

  public MemorySagaRepository(long maxOperationSize, Duration expireAfterAccess) {
    this.sagas =
        CacheBuilder.newBuilder()
            .maximumSize(maxOperationSize)
            .expireAfterAccess(expireAfterAccess.toMillis(), TimeUnit.MILLISECONDS)
            .build();
  }

  @Nonnull
  @Override
  public ListResult<Saga> list(@Nonnull ListStateCriteria criteria) {
    FromPredicate from = new FromPredicate(criteria.getNextToken());

    // TODO(rz): Support getStatuses, getStart/getEnd

    Stream<Saga> stream = sagas.asMap().values().stream().filter(s -> from.test(s.getId()));

    int count = Optional.ofNullable(criteria.getCount()).orElse(100);

    List<Saga> result = stream.limit(count).collect(Collectors.toList());

    if (result.isEmpty() || result.size() < count) {
      return new ListResultImpl(result, null);
    }

    return new ListResultImpl(result, result.get(result.size() - 1).getId());
  }

  @Nullable
  @Override
  public Saga get(@Nonnull String sagaId) {
    return sagas.getIfPresent(sagaId);
  }

  @Nullable
  @Override
  public Saga get(@Nonnull String sagaId, Instant version) {
    return get(sagaId);
  }

  @Nonnull
  @Override
  public Saga upsert(@Nonnull Saga saga) {
    Instant now = Instant.now();

    Saga storedSaga = sagas.getIfPresent(saga.getId());
    if (storedSaga == null) {
      sagas.put(saga.getId(), saga);
      return saga;
    } else {
      storedSaga.setUpdatedAt(now);
      return storedSaga;
    }
  }

  @Nonnull
  @Override
  public Saga upsert(@Nonnull SagaStep sagaStep) {
    Saga saga = sagas.getIfPresent(sagaStep.getSaga().getId());
    if (saga == null) {
      throw new MemorySagaStateException(
          "Attempting to upsert a SagaState that does not have a parent Saga");
    }

    Instant now = Instant.now();
    sagaStep.setUpdatedAt(now);
    saga.setUpdatedAt(now);

    return saga;
  }

  public static class ListResultImpl implements ListResult<Saga> {

    @Nonnull private final List<Saga> list;
    private final String nextToken;

    public ListResultImpl(@Nonnull List<Saga> list, String nextToken) {
      this.list = list;
      this.nextToken = nextToken;
    }

    @Nonnull
    @Override
    public List<Saga> getResult() {
      return list;
    }

    @Nullable
    @Override
    public String getNextToken() {
      return nextToken;
    }
  }

  private static class MemorySagaStateException extends SystemException {
    public MemorySagaStateException(String message) {
      super(message);
    }
  }

  private static class FromPredicate implements Predicate<String> {
    private boolean started = false;
    private String nextToken;

    public FromPredicate(String nextToken) {
      this.nextToken = nextToken;
    }

    @Override
    public boolean test(@Nonnull String sagaId) {
      if (nextToken == null || sagaId.equals(nextToken)) {
        started = true;
      }
      return started;
    }
  }
}
