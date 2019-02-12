/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor
import com.netflix.spinnaker.clouddriver.jobs.JobRequest
import com.netflix.spinnaker.clouddriver.jobs.JobStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AppengineJobExecutor {
  @Value('${appengine.jobSleepMs:1000}')
  Long sleepMs

  @Autowired
  JobExecutor jobExecutor

  void runCommand(List<String> command) {
    JobStatus jobStatus = jobExecutor.runJob(new JobRequest(command),
                                        System.getenv(),
                                        new ByteArrayInputStream())
    if (jobStatus.getResult() == JobStatus.Result.FAILURE && jobStatus.getStdOut()) {
      String stdOut = jobStatus.getStdOut()
      String stdErr = jobStatus.getStdErr()
      throw new IllegalArgumentException("$stdOut + $stdErr")
    }
  }
}
