/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.javascript;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Stopwatch;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.sdkcompat.javascript.BlazeJsCompletionService;
import com.intellij.lang.javascript.completion.JSCompletionService;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link JSCompletionService}. */
@RunWith(JUnit4.class)
public class JsCompletionServiceTimeoutTest extends BlazeIntegrationTestCase {
  @Test
  public void testServiceOverriden() {
    assertThat(JSCompletionService.getInstance(getProject()))
        .isInstanceOf(BlazeJsCompletionService.class);
  }

  @Test
  public void testAwaitTimesOut() {
    JSCompletionService service = JSCompletionService.getInstance(getProject());
    AtomicInteger finishedTasks = new AtomicInteger();
    Stopwatch stopwatch = Stopwatch.createStarted();
    for (int i = 0; i < 10; ++i) {
      service.submitBackgroundActivity(
          () -> {
            try {
              Thread.sleep(Duration.ofSeconds(1).toMillis());
              finishedTasks.getAndIncrement();
            } catch (InterruptedException ignored) {
              // ignored
            }
          });
    }
    assertThat(service.awaitWithTimeout()).isFalse();
    assertThat(stopwatch.elapsed()).isLessThan(Duration.ofSeconds(1));
    assertThat(finishedTasks.get()).isEqualTo(0);
  }
}
