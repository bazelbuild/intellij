/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/** Async utilities. */
public class AsyncUtil {
  public static void executeProjectChangeAction(@NotNull final Runnable task) throws Throwable {
    final ValueHolder<Throwable> error = new ValueHolder<Throwable>();

    executeOnEdt(
        new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication()
                .runWriteAction(
                    new Runnable() {
                      @Override
                      public void run() {
                        try {
                          task.run();
                        } catch (Throwable t) {
                          error.value = t;
                        }
                      }
                    });
          }
        });

    if (error.value != null) {
      throw error.value;
    }
  }

  private static void executeOnEdt(@NotNull Runnable task) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      task.run();
    } else {
      UIUtil.invokeAndWaitIfNeeded(task);
    }
  }
}
