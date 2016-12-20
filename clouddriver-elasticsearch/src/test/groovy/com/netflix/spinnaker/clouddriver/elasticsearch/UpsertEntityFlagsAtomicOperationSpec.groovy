/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityFlagsDescription
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider
import com.netflix.spinnaker.clouddriver.elasticsearch.model.EntityFlag
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.UpsertEntityFlagsAtomicOperation
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.EntityTags.EntityRef
import spock.lang.Specification

class UpsertEntityFlagsAtomicOperationSpec extends Specification {

  Front50Service front50Service
  ElasticSearchEntityTagsProvider entityTagsProvider
  UpsertEntityFlagsDescription description
  UpsertEntityFlagsAtomicOperation operation

  def setup() {
    front50Service = Mock(Front50Service)
    entityTagsProvider = Mock(ElasticSearchEntityTagsProvider)
    description = new UpsertEntityFlagsDescription()
    operation = new UpsertEntityFlagsAtomicOperation(front50Service, entityTagsProvider, description)
  }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void 'should create new tag if none exists already'() {
    given:
    EntityFlag flag = new EntityFlag(status: "info", message: "message1")
    description.entityRef = new EntityRef(cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001",
      attributes: [account: "test", region: "us-east-1"])
    description.flags = ["flag1": flag]

    when:
    operation.operate([])

    then:
    flag.created != null
    flag.createdBy == "unknown"
    flag.modified != null
    flag.modifiedBy == "unknown"
    description.tags == [ spinnaker_ui_flags: [ flag1: flag ] ]
    1 * entityTagsProvider.get('aws:servergroup:orca-v001:test:us-east-1') >> Optional.empty()
    1 * front50Service.saveEntityTags(description) >> new EntityTags(lastModified: 123, lastModifiedBy: "unknown")
    1 * entityTagsProvider.index(description)
    1 * entityTagsProvider.verifyIndex(description)
  }

  void 'should update lastModified fields, message, status on existing flags'() {
    given:
    EntityFlag existingFlag = new EntityFlag(status: "alert", message: "message0", created: 2, createdBy: "chrisb")
    EntityFlag unmodifiedFlag = new EntityFlag(status: "alert", message: "message0", created: 2, createdBy: "chrisb")
    EntityTags existingTag = new EntityTags(lastModified: 100, lastModifiedBy: "known", tags: [
            spinnaker_ui_flags: [ changing: existingFlag, unmodified: unmodifiedFlag ],
            otherTag: 'zzz'
    ])
    EntityFlag updatedFlag = new EntityFlag(status: "info", message: "message1")
    EntityFlag newFlag = new EntityFlag(status: "alert", message: "message2")
    description.entityRef = new EntityRef(cloudProvider: "aws", entityType: "servergroup", entityId: "orca-v001",
      attributes: [account: "test", region: "us-east-1"])
    description.flags = [changing: updatedFlag, brand_new: newFlag]

    when:
    operation.operate([])

    then:
    updatedFlag.created == 2L
    updatedFlag.createdBy == "chrisb"
    updatedFlag.modified != null
    updatedFlag.modifiedBy == "unknown"
    description.tags == [ spinnaker_ui_flags: [ changing: updatedFlag, unmodified: unmodifiedFlag, brand_new: newFlag ], otherTag: 'zzz' ]
    1 * entityTagsProvider.get('aws:servergroup:orca-v001:test:us-east-1') >> Optional.of(existingTag)
    1 * front50Service.saveEntityTags(description) >> new EntityTags(lastModified: 123, lastModifiedBy: "unknown")
    1 * entityTagsProvider.index(description)
    1 * entityTagsProvider.verifyIndex(description)
  }

}
