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

package com.netflix.spinnaker.clouddriver.consul.model

import com.netflix.spinnaker.clouddriver.consul.api.v1.model.ServiceResult

class ConsulService {
  String address
  Integer port
  List<String> tags
  String service
  String id

  ConsulService(ServiceResult service) {
    this.address = service.address
    this.port = service.port
    this.tags = service.tags
    this.service = service.service
    this.id = service.ID
  }

  ConsulService() {}
}
