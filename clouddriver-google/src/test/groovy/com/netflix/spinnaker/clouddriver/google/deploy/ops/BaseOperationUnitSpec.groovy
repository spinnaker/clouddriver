/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import spock.lang.Specification
import spock.lang.Shared


class BaseOperationUnitSpec extends Specification {
  @Shared
  Registry registry

  @Shared
  SafeRetry safeRetry

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)

  def doSetupSpec() {
    System.out.println("**** doSetupSpec")
    TaskRepository.threadLocalTask.set(Mock(Task))
    safeRetry = new SafeRetry(maxRetries: 10, maxWaitInterval: 60000, retryIntervalBase: 0, jitterMultiplier: 0)
    registry = new DefaultRegistry()
  }

  def preparePollingOperation(AtomicOperation operation) {
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          safeRetry: safeRetry
        )
      operation.safeRetry = safeRetry
      return operation
  }
}
