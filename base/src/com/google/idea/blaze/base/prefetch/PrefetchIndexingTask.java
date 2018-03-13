/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.prefetch;

import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;

/** Kicks off an indexing task associated with a running prefetch task, with progress dialog. */
public final class PrefetchIndexingTask extends DumbModeTask {

  private static final Logger logger = Logger.getInstance(PrefetchIndexingTask.class);

  public static void submitPrefetchingTask(
      Project project, ListenableFuture<?> task, String taskName) {
    TransactionGuard.submitTransaction(
        project,
        () -> DumbService.getInstance(project).queueTask(new PrefetchIndexingTask(task, taskName)));
  }

  private final ListenableFuture<?> future;
  private final String taskName;
  private final long startTimeMillis;

  private PrefetchIndexingTask(ListenableFuture<?> future, String taskName) {
    this.future = future;
    this.taskName = taskName;
    this.startTimeMillis = System.currentTimeMillis();
  }

  @Override
  public void performInDumbMode(ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    indicator.setText("Prefetching files...");
    while (!future.isCancelled() && !future.isDone()) {
      indicator.checkCanceled();
      TimeoutUtil.sleep(50);
    }
    long end = System.currentTimeMillis();
    logger.info(String.format("%s took: %d ms", taskName, (end - startTimeMillis)));
  }
}
