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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.UIUtil;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Used when we don't yet know all the configuration details, but want to provide a 'run/debug'
 * context action anyway.
 *
 * <p>This is necessary whenever details are expensive to calculate (e.g. involve searching for a
 * blaze target, or resolving PSI elements), because run configurations are set up on the EDT.
 */
public class PendingRunConfigurationContext implements RunConfigurationContext {
  public final ListenableFuture<RunConfigurationContext> future;
  private final String progressMessage;
  /**
   * Used to recognize previously created configs with pending details, to avoid creating duplicate
   * configs.
   */
  final String contextString;

  @Nullable private final PsiElement sourceElement;

  public PendingRunConfigurationContext(
      ListenableFuture<RunConfigurationContext> future,
      String progressMessage,
      String contextString,
      @Nullable PsiElement sourceElement) {
    this.future = future;
    this.progressMessage = progressMessage;
    this.contextString = contextString;
    this.sourceElement = sourceElement;
  }

  /**
   * Waits for the run configuration to be configured, displaying a progress dialog if necessary.
   *
   * @throws ExecutionException if the run configuration is not successfully configured
   */
  public void waitForFutureUnderProgressDialog(Project project) throws ExecutionException {
    if (future.isDone()) {
      getFutureHandlingErrors();
    }
    // The progress indicator must be created on the UI thread.
    ProgressWindow indicator =
        UIUtil.invokeAndWaitIfNeeded(
            () ->
                new BackgroundableProcessIndicator(
                    project,
                    progressMessage,
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
            future.cancel(true);
          }
        });
    try {
      getFutureHandlingErrors();
    } finally {
      if (indicator.isRunning()) {
        indicator.stop();
        indicator.processFinish();
      }
    }
  }

  RunConfigurationContext getFutureHandlingErrors() throws ExecutionException {
    try {
      RunConfigurationContext result = future.get();
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

  @Nullable
  @Override
  public PsiElement getSourceElement() {
    return sourceElement;
  }

  @Override
  public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
    config.setPendingContext(this);
    return true;
  }

  @Override
  public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
    return Objects.equals(config.getContextElementString(), contextString);
  }
}
