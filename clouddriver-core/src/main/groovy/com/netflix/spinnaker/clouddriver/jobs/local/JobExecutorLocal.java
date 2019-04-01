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
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Slf4j
public class JobExecutorLocal implements JobExecutor {
  private final long timeoutMinutes;

  public JobExecutorLocal(long timeoutMinutes) {
    this.timeoutMinutes = timeoutMinutes;
  }

  @Override
  public JobStatus runJob(final JobRequest jobRequest) {
    log.debug("Starting job: \'" + String.join(" ", jobRequest.getTokenizedCommand()) + "\'...");
    final String jobId = UUID.randomUUID().toString();
    log.debug("Executing job with tokenized command: " + String.valueOf(jobRequest.getTokenizedCommand()));

    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
    PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOut, stdErr, jobRequest.getInputStream());
    ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMinutes * 60 * 1000);
    Executor executor = new DefaultExecutor();
    executor.setStreamHandler(pumpStreamHandler);
    executor.setWatchdog(watchdog);
    executor.setExitValues(null);

    boolean success = false;
    try {
      int exitValue = executor.execute(jobRequest.getCommandLine(), jobRequest.getEnvironment());
      if (watchdog.killedProcess()) {
        log.warn("Job " + jobId + " timed out (after " + String.valueOf(timeoutMinutes) + " minutes).");
      }

      if (exitValue == 0) {
        success = true;
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute job", e);
    }

    return JobStatus.builder()
      .result(success ? JobStatus.Result.SUCCESS : JobStatus.Result.FAILURE)
      .stdOut(stdOut.toString())
      .stdErr(stdErr.toString())
      .build();
  }
}
