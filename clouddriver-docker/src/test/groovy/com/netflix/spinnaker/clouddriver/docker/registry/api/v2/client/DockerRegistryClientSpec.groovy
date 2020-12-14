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

import org.mockito.Mockito
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
//@Ignore
class DockerRegistryClientSpec extends Specification {
  private static final REPOSITORY1 = "library/ubuntu"

  @Shared
  DockerRegistryClient client
  DockerOkClientProvider defaultDockerOkClientProvider = new DefaultDockerOkClientProvider()

  def setupSpec() {

  }

  void "DockerRegistryClient should request a real set of tags."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io","","","", "", TimeUnit.MINUTES.toMillis(1),100,"","",false, defaultDockerOkClientProvider)
    def result = client.getTags(REPOSITORY1)

    then:
    result.name == REPOSITORY1
    result.tags.size() > 0
  }

  void "DockerRegistryClient should validate that it is pointing at a v2 endpoint."() {
    when:
    client = new DockerRegistryClient("https://index.docker.io","","","", "", TimeUnit.MINUTES.toMillis(1),100,"","",false, defaultDockerOkClientProvider)
    // Can only fail due to an exception thrown here.
    client.checkV2Availability()

    then:
    true
  }

  void "DockerRegistryClient invoked with insecureRegistry=true"() {
    when:
    client = new DockerRegistryClient("https://index.docker.io","","","", "", TimeUnit.MINUTES.toMillis(1),100,"","",true, defaultDockerOkClientProvider)
    DockerRegistryTags result = client.getTags(REPOSITORY1)

    then:
    result.name == REPOSITORY1
    result.tags.size() > 0
  }

  void "DockerRegistryClient uses correct user agent"() {
    setup:
    DockerRegistryClient.DockerRegistryService mockService = Mockito.mock(DockerRegistryClient.DockerRegistryService.class);

    when:
    client = new DockerRegistryClient("https://index.docker.io","email@email.com","user","password", "", TimeUnit.MINUTES.toMillis(1),100,"","",true, defaultDockerOkClientProvider, mockService)
    def userAgent = client.userAgent

    then:
    userAgent.startsWith("Spinnaker")
    //1 * client.registryService.getTags(_, _, userAgent)
  }

  void "Filtering by regular expression."() {
    setup:
    DockerRegistryClient.DockerRegistryService mockService = Mockito.mock(DockerRegistryClient.DockerRegistryService.class);

    String json = "{\"repositories\":[\"armory-io/armorycommons\",\"armory/aquascan\",\"other/keel\"]}"
    TypedInput inp = new TypedByteArray("application/json", json.getBytes())
    Response response = new Response("/v2/_catalog/",200, "nothing", Collections.EMPTY_LIST, inp)
    Mockito.when(mockService.getCatalog(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString())).thenReturn(response)

    when:
    client = new DockerRegistryClient("https://index.docker.io","email@email.com","user","password", "", TimeUnit.MINUTES.toMillis(1),100,"","",true, defaultDockerOkClientProvider, mockService)
    def original = client.getCatalog().repositories.size()
    client = new DockerRegistryClient("https://index.docker.io","email@email.com","user","password", "", TimeUnit.MINUTES.toMillis(1),100,"","armory\\/.*",true, defaultDockerOkClientProvider, mockService)
    def filtered = client.getCatalog().repositories.size()

    then:
    filtered < original
  }

}
