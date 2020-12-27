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

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import retrofit.mime.TypedInput
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/*
 * These tests all communicate with dockerhub (index.docker.io), and will either fail
 * with an exception indicating a network or HTTP error, or will fail to load data
 * from dockerhub.
 */
class DockerRegistryClientSpec extends Specification {
  private static final REPOSITORY1 = "library/ubuntu"

  @Shared
  DockerRegistryClient client
  def dockerBearerTokenService = Mock(DockerBearerTokenService)

  def stubbedRegistryService = Stub(DockerRegistryClient.DockerRegistryService){
    String tagsJson = "{\"name\":\"library/ubuntu\",\"tags\":[\"latest\",\"xenial\",\"rolling\"]}"
    TypedInput tagsTypedInput = new TypedByteArray("application/json", tagsJson.getBytes())
    Response tagsResponse = new Response("/v2/{repository}/tags/list",200, "nothing", Collections.EMPTY_LIST, tagsTypedInput)
    getTags(_,_,_) >> tagsResponse

    String checkJson = "{}"
    TypedInput checkTypedInput = new TypedByteArray("application/json", checkJson.getBytes())
    Response checkResponse = new Response("/v2/",200, "nothing", Collections.EMPTY_LIST, checkTypedInput)
    checkVersion(_,_) >> checkResponse

    String json = "{\"repositories\":[\"armory-io/armorycommons\",\"armory/aquascan\",\"other/keel\"]}"
    TypedInput catalogTypedInput = new TypedByteArray("application/json", json.getBytes())
    Response catalogResponse = new Response("/v2/_catalog/",200, "nothing", Collections.EMPTY_LIST, catalogTypedInput)
    getCatalog(_,_,_) >> catalogResponse
  }

  def setupSpec() {

  }

  void "DockerRegistryClient should request a real set of tags."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    def result = client.getTags(REPOSITORY1)

    then:
    result.name == REPOSITORY1
    result.tags.size() > 0
  }

  void "DockerRegistryClient should validate that it is pointing at a v2 endpoint."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    // Can only fail due to an exception thrown here.
    client.checkV2Availability()

    then:
    true
  }

  void "DockerRegistryClient invoked with insecureRegistry=true"() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    DockerRegistryTags result = client.getTags(REPOSITORY1)

    then:
    result.name == REPOSITORY1
    result.tags.size() > 0
  }

  void "DockerRegistryClient uses correct user agent"() {
    def mockService  = Mock(DockerRegistryClient.DockerRegistryService);
    client = new DockerRegistryClient("https://index.docker.io",100,"","",mockService, dockerBearerTokenService)

    when:
    client.checkV2Availability()
    def userAgent = client.userAgent

    then:
    userAgent.startsWith("Spinnaker")
    1 * mockService.checkVersion(_,_)
  }

  void "DockerRegistryClient should filter repositories by regular expression."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io",100,"","",stubbedRegistryService, dockerBearerTokenService)
    def original = client.getCatalog().repositories.size()
    client = new DockerRegistryClient("https://index.docker.io",100,"","armory\\/.*",stubbedRegistryService, dockerBearerTokenService)
    def filtered = client.getCatalog().repositories.size()

    then:
    filtered < original
  }

}
