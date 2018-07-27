/*
 * Copyright 2018 Bol.com
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

package com.netflix.spinnaker.clouddriver.docker.registry.metrics

import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient
import com.squareup.okhttp.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

import static java.lang.String.format

@Component
class UrlMetricsInstrumentation {

  private Logger log = LoggerFactory.getLogger(UrlMetricsInstrumentation)

  private final Registry registry

  private final Id timingId

  @Autowired
  UrlMetricsInstrumentation(Registry registry) {
    this.registry = registry
    timingId = registry.createId('httpClient.url.requestTime').withTag('className', DockerRegistryClient.simpleName)
  }

  Id getTimingId() {
    return timingId
  }

  void interceptorCompleted(Response response, Instant start, Instant end) {
    Duration duration = Duration.between(start, end)
    URL url = response.request().url()
    String path = url.getPath()
    String statusCode = Integer.toString(response.code()).substring(0,1) + "xx"
    Map<String, String> tags = new HashMap<>()

    tags.put('registry', url.getHost())
    tags.put('statusCode', statusCode)
    tags.put('success', Boolean.toString(response.isSuccessful()))

    if (path.toLowerCase().contains("/tags/list")) {
      String[] pathSegments= path.split("/")
      String repository = pathSegments[2]
      String image = pathSegments[3]
      tags.put("repository", repository)
      tags.put("image", image)
      tags.put("operation", "tags/list")
    } else {
      tags.put("operation", "other")
    }

    registry.timer(timingId.withTags(tags)).record(duration.toMillis(), TimeUnit.MILLISECONDS)
    log.info format("Received %d response for %s in %dms", response.code(), url, duration.toMillis())
  }
}
