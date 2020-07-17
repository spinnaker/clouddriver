/*
 * Copyright 2020 Google, LLC
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

package com.netflix.spinnaker.clouddriver.requestqueue.pooled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class PooledRequestQueueTest {
  DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);

  @Test
  void shouldExecuteRequests() throws Throwable {
    PooledRequestQueue queue =
        new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 1000, 1000, 1);

    assertThat(queue.execute("foo", () -> 12345L)).isEqualTo(12345L);
  }

  @Test
  void timesOutIfRequestDoesNotComplete() {
    PooledRequestQueue queue =
        new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 5000, 10, 1);

    assertThatThrownBy(
            () ->
                queue.execute(
                    "foo",
                    () -> {
                      Thread.sleep(20);
                      return 12345L;
                    }))
        .isInstanceOf(PromiseTimeoutException.class);
  }

  @Test
  void timesOutRequestIfDoesNotStartInTime() throws Throwable {
    PooledRequestQueue queue =
        new PooledRequestQueue(dynamicConfigService, new NoopRegistry(), 10, 10, 1);
    AtomicBoolean itRan = new AtomicBoolean(false);
    Callable<Void> didItRun =
        () -> {
          itRan.set(true);
          return null;
        };

    CountDownLatch latch = new CountDownLatch(1);
    Callable<Void> jerkThread =
        () -> {
          latch.countDown();
          Thread.sleep(40);
          return null;
        };

    new Thread(
            () -> {
              try {
                queue.execute("foo", jerkThread);
              } catch (PromiseTimeoutException e) {
                // expected
              } catch (Throwable t) {
                // TODO: propagate
              }
            })
        .start();

    latch.await();

    assertThatThrownBy(() -> queue.execute("foo", didItRun))
        .isInstanceOf(PromiseNotStartedException.class);
    assertThat(itRan.get()).isFalse();
  }
}
