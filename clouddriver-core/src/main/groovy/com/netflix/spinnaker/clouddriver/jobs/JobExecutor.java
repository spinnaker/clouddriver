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
package com.netflix.spinnaker.clouddriver.jobs;

import com.netflix.spinnaker.clouddriver.jobs.local.StreamConsumer;

/**
 * Executes a job defined by a JobRequest, returning the results as a JobStatus.
 *
 * The caller can optionally supply a StreamConsumer, in which case the output from the job will be
 * streamed to the StreamConsumer.
 *
 * @see JobRequest
 * @see JobStatus
 */
public interface JobExecutor {
  /**
   * Run the specified JobRequest, returning the status and the job's standard output and standard error in
   * a JobStatus object.
   * @param jobRequest The job request
   * @return The result of the job
   */
  JobStatus runJob(JobRequest jobRequest);

  /**
   * Runs the specified JobRequest, streaming output to the supplied StreamConsumer, and returns the status and standard
   * error in a JobStatus object.  The returned JobStatus will have standard output set to null, as the output will have
   * already been consumed by the supplied consumer.
   * @param jobRequest The job request
   * @param streamConsumer A callback that consumes the job's standard output as it is produced
   * @return The result of the job
   */
  JobStatus runJob(JobRequest jobRequest, StreamConsumer streamConsumer);
}
