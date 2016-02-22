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

package com.netflix.spinnaker.clouddriver.titus.model

import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import spock.lang.Specification

class TitusServerGroupSpec extends Specification {

  void 'valid server group instance is created from a titus job'() {
    given:
    Date launchDate = new Date()
    Job job = new Job(
      id: '1234',
      name: 'api-test-v000',
      applicationName: 'api.server',
      version: 'v4321',
      cpu: 1,
      memory: 2000,
      disk: 5000,
      ports: [8080],
      instances: 2,
      environment: [account: 'test'],
      submittedAt: launchDate,
      tasks: [new Job.TaskSummary(
        id: '5678',
        region: 'us-east-1',
        state: TaskState.RUNNING,
        submittedAt: launchDate,
        host: 'ec2-1-2-3-4.compute-1.amazonaws.com'
      )]
    )

    when:
    TitusServerGroup titusServerGroup = new TitusServerGroup(job)

    then:

    titusServerGroup.id == job.id
    titusServerGroup.name == job.name
    titusServerGroup.image?.dockerImageName == job.applicationName
    titusServerGroup.image?.dockerImageVersion == job.version
    titusServerGroup.resources.cpu == job.cpu
    titusServerGroup.resources.memory == job.memory
    titusServerGroup.resources.disk == job.disk
    titusServerGroup.resources.ports == job.ports.toList()
    titusServerGroup.env?.account == 'test'
    titusServerGroup.submittedAt == job.submittedAt.time
    titusServerGroup.createdTime == job.submittedAt.time
    titusServerGroup.application == 'api'
    titusServerGroup.placement.account == 'test'
    titusServerGroup.placement.region == 'us-east-1'
    titusServerGroup.instances?.size() == 1
    titusServerGroup.instances[0] instanceof TitusInstance
    titusServerGroup.instances[0].name == job.tasks[0].id
    titusServerGroup.capacity?.min == job.instances
    titusServerGroup.capacity?.max == job.instances
    titusServerGroup.capacity?.desired == job.instances
  }
}
