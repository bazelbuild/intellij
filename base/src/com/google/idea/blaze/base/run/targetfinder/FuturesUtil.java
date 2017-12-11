/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.targetfinder;

import com.intellij.openapi.diagnostic.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** Utilities operating on futures. */
public class FuturesUtil {

  private static final Logger logger = Logger.getInstance(FuturesUtil.class);

  /**
   * Blocks while calling get on the future. Use with care: logs a warning for {@link
   * ExecutionException}, and otherwise returns null on error or interrupt.
   */
  @Nullable
  public static <T> T getIgnoringErrors(Future<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn(e);
    }
    return null;
  }
}
