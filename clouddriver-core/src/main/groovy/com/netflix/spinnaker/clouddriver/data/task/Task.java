package com.netflix.spinnaker.clouddriver.data.task;

import java.util.List;
import javax.annotation.Nonnull;

/**
 * This interface represents the state of a given execution. Implementations must allow for updating
 * and completing/failing status, as well as providing the start time of the task.
 */
public interface Task {
  /** A unique identifier for the task, which can be used to retrieve it at a later time. */
  String getId();

  /** A client-provided ID used for de-duplication. */
  String getRequestId();

  /** A list of result objects that are serialized back to the caller */
  List<Object> getResultObjects();

  /**
   * This method is used to add results objects to the Task
   *
   * @param results
   */
  void addResultObjects(List<Object> results);

  /** A comprehensive history of this task's execution. */
  List<? extends Status> getHistory();

  /** The id of the clouddriver instance that submitted this task */
  String getOwnerId();

  /**
   * This method is used to update the status of the Task with given phase and status strings.
   *
   * @param phase
   * @param status
   */
  void updateStatus(String phase, String status);

  /**
   * This method will complete the task and will represent completed = true from the Task's {@link
   * #getStatus()} method.
   */
  void complete();

  /**
   * This method will fail the task and will represent completed = true and failed = true from the
   * Task's {@link #getStatus()} method.
   *
   * @deprecated Use `fail(boolean)` instead
   */
  @Deprecated
  void fail();

  /**
   * This method will fail the task and will represent completed = true and failed = true from the
   * Task's {@link #getStatus()} method.
   *
   * @param retryable If true, the failed state will be marked as retryable (sagas only)
   */
  void fail(boolean retryable);

  /**
   * This method will return the current status of the task.
   *
   * @see Status
   */
  Status getStatus();

  /** This returns the start time of the Task's execution in milliseconds since epoch form. */
  long getStartTimeMs();

  /**
   * Add a Saga to this Task. More than one Saga can be associated with a Task.
   *
   * @param sagaId The Saga name/id pair
   */
  void addSagaId(@Nonnull SagaId sagaId);

  /** Returns true if any Sagas have been associated with this Task. */
  boolean hasSagaIds();

  /** A list of Sagas associated with this Task, if any. */
  @Nonnull
  List<SagaId> getSagaIds();

  /** Returns true if the Task is retryable (in the case of a failure) */
  default boolean isRetryable() {
    if (!hasSagaIds()) {
      return false;
    }
    return getStatus().isFailed() && getStatus().isRetryable();
  }

  /** Updates the status of a failed Task to running in response to a retry operation. */
  void retry();
}
