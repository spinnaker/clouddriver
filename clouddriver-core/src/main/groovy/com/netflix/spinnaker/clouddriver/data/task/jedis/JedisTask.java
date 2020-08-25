package com.netflix.spinnaker.clouddriver.data.task.jedis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.data.task.SagaId;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskState;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonPropertyOrder({"status", "history"})
public class JedisTask implements Task {

  private static final Logger log = LoggerFactory.getLogger(JedisTask.class);

  @JsonIgnore private RedisTaskRepository repository;
  private final String id;
  private final long startTimeMs;
  private final String ownerId;
  private final String requestId;
  private final Set<SagaId> sagaIds;
  @JsonIgnore private final boolean previousRedis;

  public JedisTask(
      String id,
      long startTimeMs,
      RedisTaskRepository repository,
      String ownerId,
      String requestId,
      Set<SagaId> sagaIds,
      boolean previousRedis) {
    this.id = id;
    this.startTimeMs = startTimeMs;
    this.repository = repository;
    this.ownerId = ownerId;
    this.requestId = requestId;
    this.sagaIds = sagaIds;
    this.previousRedis = previousRedis;
  }

  @Override
  public void updateStatus(String phase, String status) {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(phase, status), this);
    log.info("[" + phase + "] " + status);
  }

  @Override
  public void complete() {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(TaskState.COMPLETED), this);
  }

  @Deprecated
  @Override
  public void fail() {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(TaskState.FAILED), this);
  }

  @Override
  public void fail(boolean retryable) {
    checkMutable();
    repository.addToHistory(
        repository
            .currentState(this)
            .update(retryable ? TaskState.FAILED_RETRYABLE : TaskState.FAILED),
        this);
  }

  @Override
  public void addResultObjects(List<Object> results) {
    checkMutable();
    if (DefaultGroovyMethods.asBoolean(results)) {
      repository.currentState(this).ensureUpdateable();
      repository.addResultObjects(results, this);
    }
  }

  public List<Object> getResultObjects() {
    return repository.getResultObjects(this);
  }

  public List<? extends Status> getHistory() {
    List<Status> status = repository.getHistory(this);
    if (status != null && !status.isEmpty() && Iterables.getLast(status).isCompleted()) {
      return status.subList(0, status.size() - 1);
    } else {
      return status;
    }
  }

  @Override
  public String getOwnerId() {
    return ownerId;
  }

  @Override
  public Status getStatus() {
    return repository.currentState(this);
  }

  @Override
  public void addSagaId(@Nonnull SagaId sagaId) {
    this.sagaIds.add(sagaId);
  }

  @Override
  public boolean hasSagaIds() {
    return !sagaIds.isEmpty();
  }

  @Override
  public void retry() {
    checkMutable();
    repository.addToHistory(repository.currentState(this).update(TaskState.STARTED), this);
  }

  private void checkMutable() {
    if (previousRedis) {
      throw new IllegalStateException("Read-only task");
    }
  }

  public RedisTaskRepository getRepository() {
    return repository;
  }

  public void setRepository(RedisTaskRepository repository) {
    this.repository = repository;
  }

  public final String getId() {
    return id;
  }

  public final long getStartTimeMs() {
    return startTimeMs;
  }

  public final String getRequestId() {
    return requestId;
  }

  public final Set<SagaId> getSagaIds() {
    return sagaIds;
  }

  public final boolean getPreviousRedis() {
    return previousRedis;
  }

  public final boolean isPreviousRedis() {
    return previousRedis;
  }
}
