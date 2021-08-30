/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.testframework.fixtures;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester;
import com.intellij.util.ThrowableRunnable;

/**
 * #api202 {@link CompletionAutoPopupTester#runWithAutoPopupEnabled} now accepts {@link
 * ThrowableRunnable} instead of {@link Runnable} and can throw an exception
 */
public class CompletionAutoPopupTesterAdapter extends CompletionAutoPopupTester {

  public CompletionAutoPopupTesterAdapter(CodeInsightTestFixture fixture) {
    super(fixture);
  }

  @SuppressWarnings({"RedundantThrows", "FunctionalInterfaceClash"})
  public void runWithAutoPopupEnabled(ThrowableRunnable<Throwable> r) throws Throwable {
    super.runWithAutoPopupEnabled(
        () -> {
          try {
            r.run();
          } catch (Throwable throwable) {
            // the run cannot throw an exception, this is to satisfy the compiler
          }
        });
  }
}
