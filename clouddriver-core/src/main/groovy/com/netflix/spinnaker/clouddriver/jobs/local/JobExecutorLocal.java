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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.UUID;

@Slf4j
public class JobExecutorLocal implements JobExecutor {
  private final long timeoutMinutes;

  public JobExecutorLocal(long timeoutMinutes) {
    this.timeoutMinutes = timeoutMinutes;
  }

  @Override
  public JobStatus<String> runJob(final JobRequest jobRequest) {
    return executeWrapper(jobRequest, this::execute);
  }

  @Override
  public <T> JobStatus<T> runJob(final JobRequest jobRequest, StreamConsumer<T> streamConsumer) {
    return executeWrapper(jobRequest, request -> executeStreaming(request, streamConsumer));
  }

  private <T> JobStatus<T> executeWrapper(final JobRequest jobRequest, ResultSupplier<T> resultSupplier) {
    log.debug(String.format("Starting job: '%s'...", String.join(" ", jobRequest.getTokenizedCommand())));
    final String jobId = UUID.randomUUID().toString();

    JobStatus<T> jobStatus;
    try {
      jobStatus = resultSupplier.supply(jobRequest);
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute job", e);
    }

    if (jobStatus.isKilled()) {
      log.warn(String.format("Job %s timed out (after %d minutes)", jobId, timeoutMinutes));
    }

    return jobStatus;
  }

  private JobStatus<String> execute(JobRequest jobRequest) throws IOException {
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

    Executor executor = buildExecutor(new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream()));
    int exitValue = executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment());

    return JobStatus.<String>builder()
      .result(exitValue == 0 ? JobStatus.Result.SUCCESS : JobStatus.Result.FAILURE)
      .killed(executor.getWatchdog().killedProcess())
      .stdOut(stdOut.toString())
      .stdErr(stdErr.toString())
      .build();
  }

  private <T> JobStatus<T> executeStreaming(JobRequest jobRequest, StreamConsumer<T> consumer) throws IOException {
    PipedOutputStream stdOut = new PipedOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

    Executor executor = buildExecutor(new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream()));
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
    executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment(), resultHandler);

    T result = consumer.consume(new PipedInputStream(stdOut));

    try {
      resultHandler.waitFor();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return JobStatus.<T>builder()
      .result(resultHandler.getExitValue() == 0 ? JobStatus.Result.SUCCESS : JobStatus.Result.FAILURE)
      .killed(executor.getWatchdog().killedProcess())
      .stdOut(result)
      .stdErr(stdErr.toString())
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

  interface ResultSupplier<U> {
    JobStatus<U> supply(JobRequest jobRequest) throws IOException;
  }
}
