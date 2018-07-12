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

import com.squareup.okhttp.Interceptor
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response

import java.time.Instant

class UrlMetricsInterceptor implements Interceptor {

  private UrlMetricsInstrumentation urlMetricsInstrumentation;

  UrlMetricsInterceptor(UrlMetricsInstrumentation urlMetricsInstrumentation) {
    this.urlMetricsInstrumentation = urlMetricsInstrumentation
  }

  Response intercept(Chain chain) throws IOException {
    Request request = chain.request()
    Instant requestStartTime = Instant.now()

    Response response = chain.proceed(request)
    Instant requestEndTime = Instant.now()
    urlMetricsInstrumentation.interceptorCompleted(response, requestStartTime, requestEndTime)
    return response
  }
}
