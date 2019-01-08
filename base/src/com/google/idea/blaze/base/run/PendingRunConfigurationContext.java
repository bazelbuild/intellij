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
package com.google.idea.blaze.base.run;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;

/**
 * Used when we don't yet know all the configuration details, but want to provide a 'run/debug'
 * context action anyway.
 *
 * <p>This is necessary whenever details are expensive to calculate (e.g. involve searching for a
 * blaze target, or resolving PSI elements), because run configurations are set up on the EDT.
 */
public interface PendingRunConfigurationContext extends RunConfigurationContext {

  ListenableFuture<RunConfigurationContext> getFuture();

  String getProgressMessage();

  /**
   * Returns a future with all currently-unknown details of this configuration context resolved.
   *
   * <p>Handles the case where there are nested {@link PendingRunConfigurationContext}s.
   */
  static ListenableFuture<RunConfigurationContext> recursivelyResolveContext(
      ListenableFuture<RunConfigurationContext> future) {
    return Futures.transformAsync(
        future,
        c ->
            c instanceof PendingRunConfigurationContext
                ? recursivelyResolveContext(((PendingRunConfigurationContext) c).getFuture())
                : Futures.immediateFuture(c),
        MoreExecutors.directExecutor());
  }

  /**
   * Waits for the run configuration to be configured, displaying a progress dialog if necessary.
   *
   * @throws ExecutionException if the run configuration is not successfully configured
   */
  static void waitForFutureUnderProgressDialog(
      Project project, PendingRunConfigurationContext pendingContext) throws ExecutionException {
    if (pendingContext.getFuture().isDone()) {
      getFutureHandlingErrors(pendingContext);
    }
    // The progress indicator must be created on the UI thread.
    ProgressWindow indicator =
        UIUtil.invokeAndWaitIfNeeded(
            () ->
                new BackgroundableProcessIndicator(
                    project,
                    pendingContext.getProgressMessage(),
                    PerformInBackgroundOption.ALWAYS_BACKGROUND,
                    "Cancel",
                    "Cancel",
                    /* cancellable= */ true));

    indicator.setIndeterminate(true);
    indicator.start();
    indicator.addStateDelegate(
        new AbstractProgressIndicatorExBase() {
          @Override
          public void cancel() {
            super.cancel();
            pendingContext.getFuture().cancel(true);
          }
        });
    try {
      getFutureHandlingErrors(pendingContext);
    } finally {
      if (indicator.isRunning()) {
        indicator.stop();
        indicator.processFinish();
      }
    }
  }

  static RunConfigurationContext getFutureHandlingErrors(
      PendingRunConfigurationContext pendingContext) throws ExecutionException {
    try {
      RunConfigurationContext result = pendingContext.getFuture().get();
      if (result == null) {
        throw new ExecutionException("Run configuration setup failed.");
      }
      return result;
    } catch (InterruptedException e) {
      throw new RunCanceledByUserException();
    } catch (java.util.concurrent.ExecutionException e) {
      throw new ExecutionException(e);
    }
  }
}
