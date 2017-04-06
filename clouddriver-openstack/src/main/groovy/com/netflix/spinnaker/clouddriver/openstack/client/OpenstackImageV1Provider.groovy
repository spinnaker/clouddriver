/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import org.openstack4j.model.image.Image

class OpenstackImageV1Provider implements OpenstackImageProvider, OpenstackRequestHandler, OpenstackIdentityAware {

  OpenstackIdentityProvider identityProvider

  OpenstackImageV1Provider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  @Override
  List<Image> listImages(String region, Map<String, String> filters) {
    handleRequest {
      getRegionClient(region).images().listAll(filters)
    }
  }

  @Override
  List<Image> listImages(String region) {
    handleRequest {
      getRegionClient(region).images().listAll(null)
    }
  }

}
