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

import com.netflix.titanclient.model.Job
import com.netflix.titanclient.model.Task
import com.netflix.titanclient.model.TaskState
import spock.lang.Specification

class TitanServerGroupSpec extends Specification {

  void 'valid server group instance is created from a titan job'() {
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
      env: [application: 'api', account: 'test', region: 'us-east-1'],
      submittedAt: launchDate,
      tasks: [new Task(
        id: '5678',
        jobId: '1234',
        state: TaskState.RUNNING,
        applicationName: 'api.server',
        version: 'v4321',
        cpu: 1,
        memory: 2000,
        disk: 5000,
        ports: [(8080): 7150],
        submittedAt: launchDate,
        env: [application: 'api', account: 'test', region: 'us-east-1'],
        host: 'ec2-1-2-3-4.compute-1.amazonaws.com'
      )]
    )

    when:
    TitanServerGroup titanServerGroup = new TitanServerGroup(job)

    then:

    titanServerGroup.id == job.id
    titanServerGroup.name == job.name
    titanServerGroup.image?.dockerImageName == job.dockerImageName
    titanServerGroup.image?.dockerImageVersion == job.dockerImageVersion
    titanServerGroup.resources.cpu == job.cpu
    titanServerGroup.resources.memory == job.memory
    titanServerGroup.resources.disk == job.disk
    titanServerGroup.resources.ports == Arrays.asList(job.ports)
    titanServerGroup.env?.application == 'api'
    titanServerGroup.env?.account == 'test'
    titanServerGroup.env?.region == 'us-east-1'
    titanServerGroup.submittedAt == job.submittedAt.time
    titanServerGroup.createdTime == job.submittedAt.time
    titanServerGroup.application == job.application
    titanServerGroup.placement.account == job.account
    titanServerGroup.placement.region == job.region
    titanServerGroup.placement.subnetId == job.subnetId
    titanServerGroup.instances?.size() == 1
    titanServerGroup.instances[0] instanceof TitanInstance
    titanServerGroup.instances[0].name == job.tasks[0].id
    titanServerGroup.capacity?.min == job.instances
    titanServerGroup.capacity?.max == job.instances
    titanServerGroup.capacity?.desired == job.instances
  }
}
