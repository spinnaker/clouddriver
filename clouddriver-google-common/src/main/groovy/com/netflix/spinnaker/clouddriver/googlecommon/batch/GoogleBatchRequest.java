/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.googlecommon.batch;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.common.collect.Iterables;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for sending batch requests to GCE.
 */
@Slf4j
public class GoogleBatchRequest {

  private static final Integer MAX_BATCH_SIZE = 100; // Platform specified max to not overwhelm batch backends.
  private static final Integer DEFAULT_CONNECT_TIMEOUT_MILLIS = 2 * 60000;
  private static final Integer DEFAULT_READ_TIMEOUT_MILLIS = 2 * 60000;

  private ArrayList<QueuedRequest> queuedRequests;
  private String clouddriverUserAgentApplicationName;
  private Compute compute;

  public GoogleBatchRequest(Compute compute, String clouddriverUserAgentApplicationName) {
    this.compute = compute;
    this.clouddriverUserAgentApplicationName = clouddriverUserAgentApplicationName;
    this.queuedRequests = new ArrayList<>();
  }

  public void execute() throws IOException {
    if (queuedRequests.size() <= 0) {
      log.debug("No requests queued in batch, exiting.");
      return;
    }

    List<BatchRequest> queuedBatches = new ArrayList<>();
    Iterable<List<QueuedRequest>> requestPartitions = Iterables.partition(queuedRequests, MAX_BATCH_SIZE);
    for (List<QueuedRequest> requestPart : requestPartitions) {
      BatchRequest newBatch = newBatch();
      requestPart.forEach(qr -> {
          try {
            qr.getRequest().queue(newBatch, qr.getCallback());
          } catch (IOException ioe) {
            log.error("Queueing request {} in batch failed.", qr, ioe);
          }
        });
      queuedBatches.add(newBatch);
    }

    queuedBatches.parallelStream()
      .forEach(b -> {
        try {
          b.execute();
        } catch (IOException ioe) {
          log.error("Executing batch request {} failed.", b, ioe);
        }
      });
  }

  private BatchRequest newBatch() {
    return compute.batch(
      new HttpRequestInitializer() {
        @Override
        public void initialize(HttpRequest request) throws IOException {
          request.getHeaders().setUserAgent(clouddriverUserAgentApplicationName);
          request.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MILLIS); // 2 minutes connect timeout
          request.setReadTimeout(DEFAULT_READ_TIMEOUT_MILLIS); // 2 minutes read timeout
        }
      }
    );
  }

  public void queue(ComputeRequest request, JsonBatchCallback callback) {
    queuedRequests.add(new QueuedRequest(request, callback));
  }

  public Integer size() {
    return queuedRequests.size();
  }

  @Data
  @AllArgsConstructor
  private static class QueuedRequest {
    private ComputeRequest request;
    private JsonBatchCallback callback;
  }
}
