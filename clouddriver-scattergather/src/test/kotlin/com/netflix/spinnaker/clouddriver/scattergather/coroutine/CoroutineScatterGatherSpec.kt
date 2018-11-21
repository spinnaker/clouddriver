/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather.coroutine

import com.netflix.spinnaker.clouddriver.scattergather.ServletScatterGatherRequest
import com.netflix.spinnaker.clouddriver.scattergather.client.DefaultScatteredOkHttpCallFactory
import com.netflix.spinnaker.clouddriver.scattergather.reducer.DeepMergeResponseReducer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.springframework.mock.web.MockHttpServletRequest
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

internal object CoroutineScatterGatherSpec : Spek({

  describe("calling multiple targets with coroutines") {
    val server = MockWebServer()

    beforeGroup {
      server.enqueue(MockResponse().setBody("""{"one": 1}"""))
      server.enqueue(MockResponse().setBody("""{"two": 2}"""))
      server.start()
    }

    given("two requests") {
      val subject = CoroutineScatterGather(DefaultScatteredOkHttpCallFactory(
        OkHttpClient()
      ))

      it("completes the requests") {
        val result = subject.request(
          ServletScatterGatherRequest(
            mapOf(
              "shard1" to server.url("").toString(),
              "shard2" to server.url("").toString()
            ),
            MockHttpServletRequest("GET", "hello").apply {
              addHeader("Accept", "application/json")
            }
          ),
          DeepMergeResponseReducer()
        )

        expectThat(result) {
          get { status }.isEqualTo(200)
          get { body!! }.contains("two")
          get { body!! }.contains("one")
        }
      }
    }

    afterGroup {
      server.shutdown()
    }
  }
})
