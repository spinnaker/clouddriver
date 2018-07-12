/*
 * Copyright 2018 Netflix, Inc.
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
  private final Id counterId

  @Autowired
  UrlMetricsInstrumentation(Registry registry) {
    this.registry = registry
    timingId = registry.createId('UrlRequestTime').withTag('className', UrlMetricsInstrumentation.simpleName)
    counterId = registry.createId('UrlCount').withTag('className', UrlMetricsInstrumentation.simpleName)
  }

  Id getTimingId() {
    return timingId
  }

  Id getCounterId() {
    return counterId
  }

  void interceptorCompleted(Response response, Instant start, Instant end) {
    Duration duration = Duration.between(start, end)
    URL url = response.request().url()
    registry.timer(timingId.withTag('host', url.getHost()).withTag('path', url.getPath())).record(duration.toMillis(), TimeUnit.MILLISECONDS)
    registry.counter(counterId.withTag('host', url.getHost()).withTag('path', url.getPath()).withTag('successful', response.isSuccessful())).increment()
    log.info format("Received %d response for %s in %dms", response.code(), url, duration.toMillis())
  }
}
