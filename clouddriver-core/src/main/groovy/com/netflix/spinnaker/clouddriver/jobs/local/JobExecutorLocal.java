/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.clouddriver.jobs.local;

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobStatus;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;

import java.io.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class JobExecutorLocal implements JobExecutor {
  private final long timeoutMinutes;

  public JobExecutorLocal(long timeoutMinutes) {
    this.timeoutMinutes = timeoutMinutes;
  }

  @Override
  public JobStatus runJob(final JobRequest jobRequest) {
    return runJob(jobRequest, null);
  }

  @Override
  public JobStatus runJob(final JobRequest jobRequest, StreamConsumer streamConsumer) {
    log.debug(String.format("Starting job: '%s'...", String.join(" ", jobRequest.getTokenizedCommand())));

    final String jobId = UUID.randomUUID().toString();
    ExecuteResult executeResult;
    try {
      if (streamConsumer != null) {
        executeResult = executeJobStreaming(jobRequest, streamConsumer);
      } else {
        executeResult = executeJob(jobRequest);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute job", e);
    }

    if (executeResult.wasKilled) {
      log.warn(String.format("Job %s timed out (after %d minutes)", jobId, timeoutMinutes));
    }

    return JobStatus.builder()
      .result(executeResult.exitValue == 0 ? JobStatus.Result.SUCCESS : JobStatus.Result.FAILURE)
      .stdOut(executeResult.getStdOut())
      .stdErr(executeResult.getStdErr())
      .build();
  }

  private ExecuteResult executeJob(JobRequest jobRequest) throws IOException {
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

    Executor executor = buildExecutor(new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream()));
    int exitValue = executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment());

    return ExecuteResult.builder()
      .stdOut(stdOut)
      .stdErr(stdErr)
      .exitValue(exitValue)
      .wasKilled(executor.getWatchdog().killedProcess())
      .build();
  }

  private ExecuteResult executeJobStreaming(JobRequest jobRequest, StreamConsumer consumer) throws IOException {
    PipedOutputStream stdOut = new PipedOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

    Executor executor = buildExecutor(new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream()));
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
    executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment(), resultHandler);

    try (PipedInputStream is = new PipedInputStream(stdOut)) {
      consumer.consume(is);
    }

    try {
      resultHandler.waitFor();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return ExecuteResult.builder()
      .stdErr(stdErr)
      .exitValue(resultHandler.getExitValue())
      .wasKilled(executor.getWatchdog().killedProcess())
      .build();
  }

  private Executor buildExecutor(ExecuteStreamHandler streamHandler) {
    Executor executor = new DefaultExecutor();
    executor.setStreamHandler(streamHandler);
    executor.setWatchdog(new ExecuteWatchdog(timeoutMinutes * 60 * 1000));
    // Setting this to null causes the executor to skip verifying exit codes; we'll handle checking the exit status
    // instead of having the executor throw an exception for non-zero exit codes.
    executor.setExitValues(null);

    return executor;
  }

  @Builder
  private static class ExecuteResult {
    final OutputStream stdOut;
    final OutputStream stdErr;
    final int exitValue;
    final boolean wasKilled;

    String getStdOut() {
      return Optional.ofNullable(stdOut).map(Objects::toString).orElse(null);
    }

    String getStdErr() {
      return Optional.ofNullable(stdErr).map(Objects::toString).orElse(null);
    }
  }
}
