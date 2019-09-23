/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.event

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.event.exceptions.UninitializedEventException

abstract class AbstractSpinnakerEvent : SpinnakerEvent {
  /**
   * Not a lateinit to make Java/Lombok & Jackson compatibility a little easier, although behavior is exactly the same.
   */
  @JsonIgnore
  private var metadata: EventMetadata? = null

  override fun getMetadata(): EventMetadata {
    return metadata ?: throw UninitializedEventException()
  }

  override fun setMetadata(eventMetadata: EventMetadata) {
    metadata = eventMetadata
  }
}
