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
package com.netflix.spinnaker.clouddriver.google

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class GoogleExecutor {
  public static final  String TAG_SCOPE = GoogleExecutorTraits.TAG_SCOPE
  public static final  String TAG_REGION = GoogleExecutorTraits.TAG_REGION
  public static final  String TAG_ZONE = GoogleExecutorTraits.TAG_ZONE
  public static final  String TAG_BATCH_CONTEXT = "context"
  public static final  String SCOPE_BATCH = "batch"
  public static final  String SCOPE_GLOBAL = GoogleExecutorTraits.SCOPE_GLOBAL
  public static final  String SCOPE_REGIONAL = GoogleExecutorTraits.SCOPE_REGIONAL
  public static final  String SCOPE_ZONAL = GoogleExecutorTraits.SCOPE_ZONAL

  static Registry globalRegistry

  public static void setGlobalRegisry(Registry registry) {
    globalRegistry = registry;
  }

  public static Registry getGlobalRegistry() {
    return globalRegistry
  }

  public static void timeExecuteBatch(BatchRequest batch, String batchContext, String... tags)
    throws IOException {
     Registry registry = globalRegistry
     Id id = registry.createId("google.batch.execute", tags).withTags(TAG_BATCH_CONTEXT, batchContext, TAG_SCOPE, SCOPE_BATCH)
     Id sizeId = registry.createId("google.batchSize", tags).withTag(TAG_BATCH_CONTEXT, batchContext)

     long startTime = System.nanoTime()

     try {
       batch.execute()
       long nanos = System.nanoTime() - startTime
       registry.timer(id.withTag("success", "true")).record(nanos, TimeUnit.NANOSECONDS)
       registry.counter(sizeId.withTag("success", "true")).increment(batch.size())
     } catch (IOException ioex) {
       registry.timer(id.withTag("success", "false")).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
       registry.counter(sizeId.withTag("success", "false")).increment(batch.size())
       throw ioex
     } catch (Exception ex) {
       registry.timer(id.withTag("success", "false")).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
       registry.counter(sizeId.withTag("success", "false")).increment(batch.size())
       throw new IllegalStateException(ex);
     }
  }

  public static <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags) throws IOException {
     Registry registry = globalRegistry
     Id id = registry.createId("google.api", tags).withTag("api", api)
     long startTime = System.nanoTime()

     try {
       T result = request.execute()
       long nanos = System.nanoTime() - startTime
       registry.timer(id.withTag("success", "true")).record(nanos, TimeUnit.NANOSECONDS)
       return result
     } catch (IOException ioex) {
       registry.timer(id.withTag("success", "false")).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
       throw ioex;
     } catch (Exception ex) {
       registry.timer(id.withTag("success", "false")).record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
       throw new IllegalStateException(ex);
     }
  }
}
