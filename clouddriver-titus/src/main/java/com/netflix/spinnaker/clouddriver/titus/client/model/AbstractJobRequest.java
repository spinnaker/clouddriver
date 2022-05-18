/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client.model;

public class AbstractJobRequest {
  private String user;
  private String jobId;

  public String getUser() {
    return user;
  }

  public AbstractJobRequest withUser(String user) {
    this.user = user;
    return this;
  }

  public String getJobId() {
    return jobId;
  }

  public AbstractJobRequest withJobId(String jobId) {
    this.jobId = jobId;
    return this;
  }
}
