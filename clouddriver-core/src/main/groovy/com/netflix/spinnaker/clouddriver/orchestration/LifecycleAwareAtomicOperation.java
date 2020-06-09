/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.clouddriver.orchestration;

import static java.lang.String.format;

import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper class for {@link AtomicOperation}s that support lifecycles.
 *
 * <p>This is an internal class used by {@link OperationsService} to correctly invoke lifecycle
 * methods when requested, without having to do intrusive refactors of a {@link
 * OrchestrationProcessor}.
 *
 * <p>Since {@link DefaultOrchestrationProcessor} does not actually care what the return object type
 * of an AtomicOperation is, we can safely use {@code Object} as the return type for before/after
 * operations.
 */
public class LifecycleAwareAtomicOperation implements AtomicOperation<Object> {

  private static final Logger log = LoggerFactory.getLogger(LifecycleAwareAtomicOperation.class);

  private final AtomicOperation<?> atomicOperation;
  private final AtomicOperation.OperationLifecycle operationLifecycle;

  public LifecycleAwareAtomicOperation(
      AtomicOperation<?> atomicOperation, OperationLifecycle operationLifecycle) {
    this.atomicOperation = atomicOperation;
    this.operationLifecycle = operationLifecycle;
  }

  @Override
  public Object operate(List priorOutputs) {
    log.debug(
        "Running '{}' lifecycle for '{}'",
        operationLifecycle,
        atomicOperation.getClass().getSimpleName());
    switch (operationLifecycle) {
      case BEFORE:
        return atomicOperation.beforeOperate();
      case AFTER:
        return atomicOperation.afterOperate();
      default:
        throw new SystemException(format("Unsupported lifecycle: '%s'", operationLifecycle));
    }
  }
}
