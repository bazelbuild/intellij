/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.javascript.completion.JSCompletionService;
import com.intellij.openapi.application.ApplicationManager;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Replacement {@link JSCompletionService} that fixes a buggy timeout implementation. */
public class BlazeJsCompletionService extends JSCompletionService {
  public BlazeJsCompletionService(LookupManager lookupManager) {
    super(lookupManager);
  }

  /**
   * Upstream uses {@link java.util.concurrent.ForkJoinPool#awaitQuiescence(long, TimeUnit)}, which
   * executes tasks directly on the caller thread (EDT) and could ignore the hard coded 100ms
   * timeout.
   */
  @Override
  public boolean awaitWithTimeout() {
    try {
      return ApplicationManager.getApplication()
          .executeOnPooledThread(super::awaitWithTimeout)
          .get(100L, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return false;
    }
  }
}
