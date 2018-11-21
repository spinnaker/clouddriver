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

import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer
import com.netflix.spinnaker.clouddriver.scattergather.ScatterGather
import com.netflix.spinnaker.clouddriver.scattergather.ScatterGatherException
import com.netflix.spinnaker.clouddriver.scattergather.ServletScatterGatherRequest
import com.netflix.spinnaker.clouddriver.scattergather.client.ScatteredOkHttpCallFactory
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Performs a scatter/gather request using coroutines.
 *
 * A requests for a particular scatter/gather operation will be given a timeout
 * which they need to complete before failing the entire scatter operation.
 * When a timeout happens, all on-going requests will be cancelled.
 */
class CoroutineScatterGather(
  private val callFactory: ScatteredOkHttpCallFactory
) : ScatterGather {

  override fun request(request: ServletScatterGatherRequest, reducer: ResponseReducer): ReducedResponse {
    val responses = performScatter(
      callFactory.createCalls(
        UUID.randomUUID().toString(),
        request.targets,
        request.original
      ),
      request.timeout
    )

    return reducer.reduce(responses)
  }

  private fun performScatter(calls: Collection<Call>, timeout: Duration): List<Response> {
    // Could do `withTimeoutOrNull`, but we want to capture & wrap the timeout exception
    try {
      return runBlocking(Dispatchers.IO) {
        withTimeout(timeout.toMillis()) {
          calls
            .map { call ->
              async { call.await() }
            }
            .map { it.await() }
        }
      }
    } catch (e: TimeoutCancellationException) {
      throw ScatterGatherException("Scatter failed to complete all requests: ${e.message}", e)
    }
  }
}

private class ContinuationCallback(
  private val continuation: CancellableContinuation<Response>
) : Callback {

  override fun onFailure(call: Call, e: IOException) {
    if (e.isRetryable()) {
      call.enqueue(this)
    } else {
      continuation.resumeWithException(e)
    }
  }

  override fun onResponse(call: Call, response: Response) {
    if (retryableCodes.contains(response.code())) {
      call.enqueue(this)
    } else {
      continuation.resume(response)
    }
  }

  private fun IOException.isRetryable(): Boolean {
    return this is SocketTimeoutException
  }

  companion object {
    private val retryableCodes = listOf(429, 503)
  }
}

private suspend fun Call.await(): Response {
  return suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
      cancel()
    }
    enqueue(ContinuationCallback(continuation))
  }
}
