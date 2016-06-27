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
package com.google.idea.blaze.base.async.executor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

/**
 * Shared thread pool for blaze tasks.
 */
public abstract class BlazeExecutor {
  public static enum Modality {
    MODAL, // This task must start in the foreground and stay there.
    BACKGROUNDABLE, // This task will start in the foreground, but can be sent to the background.
    ALWAYS_BACKGROUND // This task will start in the background and stay there.
  }

  @NotNull
  public static BlazeExecutor getInstance() {
    return ServiceManager.getService(BlazeExecutor.class);
  }

  public abstract <T> ListenableFuture<T> submit(Callable<T> callable);

  public abstract ListeningExecutorService getExecutor();

  public static ListenableFuture<Void> submitTask(
    @Nullable final Project project,
    @NotNull final Progressive progressive) {
    return submitTask(project, "", progressive);
  }

  public static ListenableFuture<Void> submitTask(
    @Nullable final Project project,
    @NotNull final String title,
    @NotNull final Progressive progressive) {
    return submitTask(
      project,
      title,
      true /* cancelable */,
      Modality.ALWAYS_BACKGROUND,
      progressive);
  }

  public static ListenableFuture<Void> submitTask(
    @Nullable final Project project,
    @NotNull final String title,
    final boolean cancelable,
    final Modality modality,
    @NotNull final Progressive progressive) {

    // The progress indicator must be created on the UI thread.
    final ProgressWindow indicator = UIUtil.invokeAndWaitIfNeeded(new Computable<ProgressWindow>() {
      @Override
      public ProgressWindow compute() {
        if (modality == Modality.MODAL) {
          ProgressWindow indicator = new ProgressWindow(cancelable, project);
          indicator.setTitle(title);
          return indicator;
        }
        else {
          PerformInBackgroundOption backgroundOption = modality == Modality.BACKGROUNDABLE ?
                                                       PerformInBackgroundOption.DEAF :
                                                       PerformInBackgroundOption.ALWAYS_BACKGROUND;
          return new BackgroundableProcessIndicator(
            project,
            title,
            backgroundOption,
            "Cancel",
            "Cancel",
            cancelable
          );
        }
      }
    });

    indicator.setIndeterminate(true);
    indicator.start();
    final Runnable process = new Runnable() {
      @Override
      public void run() {
        progressive.run(indicator);
      }
    };
    final ListenableFuture<Void> future = getInstance().submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        ProgressManager.getInstance().runProcess(process, indicator);
        return null;
      }
    });
    if (cancelable) {
      indicator.addStateDelegate(new AbstractProgressIndicatorExBase() {
        @Override
        public void cancel() {
          super.cancel();
          future.cancel(true);
        }
      });
    }
    return future;
  }
}
