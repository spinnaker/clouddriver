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

import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.docker.registry.metrics.UrlMetricsInstrumentation
import org.slf4j.Logger
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/*
 * These tests all communicate with dockerhub (index.docker.io), and will either fail
 * with an exception indicating a network or HTTP error, or will fail to load data
 * from dockerhub.
 */
@Ignore
class DockerRegistryClientSpec extends Specification {
  private static final REGISTRY_HOST = "index.docker.io"
  private static final REGISTRY_URL = "https://" + REGISTRY_HOST
  private static final REPOSITORY1 = "library/ubuntu"
  private UrlMetricsInstrumentation urlMetricsInstrumentation
  private Registry registry

  @Shared
  DockerRegistryClient client

  def setup() {
    registry = new DefaultRegistry()
    urlMetricsInstrumentation = new UrlMetricsInstrumentation(registry)
  }

  def setupSpec() {

  }

  void "DockerRegistryClient should request a real set of tags."() {
    when:
      client = new DockerRegistryClient(REGISTRY_URL, "", "", "", TimeUnit.MINUTES.toMillis(1), 100, "", false, urlMetricsInstrumentation)
      DockerRegistryTags result = client.getTags(REPOSITORY1)

    then:
      result.name == REPOSITORY1
      result.tags.size() > 0
  }

  void "DockerRegistryClient should validate that it is pointing at a v2 endpoint."() {
    when:
      client = new DockerRegistryClient(REGISTRY_URL, "", "", "", TimeUnit.MINUTES.toMillis(1), 100, "", false, urlMetricsInstrumentation)
      // Can only fail due to an exception thrown here.
      client.checkV2Availability()

    then:
      true
  }

  void "DockerRegistryClient invoked with insecureRegistry=true"() {
    when:
      client = new DockerRegistryClient(REGISTRY_URL, "", "", "", TimeUnit.MINUTES.toMillis(1), 100, "", true, urlMetricsInstrumentation)
      DockerRegistryTags result = client.getTags(REPOSITORY1)

    then:
      result.name == REPOSITORY1
      result.tags.size() > 0
  }

  void "DockerRegistryClient uses correct user agent"() {
    when:
    client = new DockerRegistryClient(REGISTRY_URL, "", "", "", TimeUnit.MINUTES.toMillis(1), 100, "", true, urlMetricsInstrumentation)
    client.registryService = Mock(DockerRegistryClient.DockerRegistryService)

    def userAgent = client.userAgent
    client.getTags(REPOSITORY1)

    then:
    userAgent.startsWith("Spinnaker")
    1 * client.registryService.getTags(_, _, userAgent)
  }

  void "DockerRegistryClient emits Spectator metrics when calling an URL"() {
    given:
      def url_path = '/v2/' + REPOSITORY1 + '/tags/list'
      client = new DockerRegistryClient(REGISTRY_URL, "", "", "", TimeUnit.MINUTES.toMillis(1), 100, "", true, urlMetricsInstrumentation)
      urlMetricsInstrumentation.log = Mock(Logger)
    when:
      client.getTags(REPOSITORY1)
    then:
      // The endpoint gets called twice; first with a 401, then with a 200. They both get logged and metered, so:
      registry.timer(urlMetricsInstrumentation.getTimingId().withTag('host', REGISTRY_HOST).withTag('path', url_path)).totalTime() > 0
      registry.counter(urlMetricsInstrumentation.getCounterId().withTag('host', REGISTRY_HOST).withTag('path', url_path).withTag('successful', true)).count() == 1
      registry.counter(urlMetricsInstrumentation.getCounterId().withTag('host', REGISTRY_HOST).withTag('path', url_path).withTag('successful', false)).count() == 1
      2 * urlMetricsInstrumentation.log.info(_ as String)
  }
}
