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
package com.google.idea.blaze.base.scope.scopes;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.NotNull;

/**
 * Notifies the user with a system notification when the scope ends.
 */
public class NotificationScope implements BlazeScope {

  private static final long NOTIFICATION_THRESHOLD_MS = 0;

  @NotNull
  private Project project;

  @NotNull
  private final String notificationName;

  @NotNull
  private final String notificationTitle;

  @NotNull
  private final String notificationText;

  @NotNull
  private final String notificationErrorText;

  private long startTime;

  public NotificationScope(
    @NotNull Project project,
    @NotNull String notificationName,
    @NotNull String notificationTitle,
    @NotNull String notificationText,
    @NotNull String notificationErrorText) {
    this.project = project;
    this.notificationName = notificationName;
    this.notificationTitle = notificationTitle;
    this.notificationText = notificationText;
    this.notificationErrorText = notificationErrorText;
  }

  @Override
  public void onScopeBegin(@NotNull BlazeContext context) {
    startTime = System.currentTimeMillis();
  }

  @Override
  public void onScopeEnd(@NotNull BlazeContext context) {
    if (project.isDisposed()) {
      return;
    }
    if (context.isCancelled()) {
      context.output(new PrintOutput(notificationName + " cancelled"));
      return;
    }
    long duration = System.currentTimeMillis() - startTime;
    if (duration < NOTIFICATION_THRESHOLD_MS) {
      return;
    }

    String notificationText = !context.hasErrors()
                              ? this.notificationText
                              : this.notificationErrorText;

    SystemNotifications.getInstance().notify(
      notificationName,
      notificationTitle,
      notificationText);

    if (context.hasErrors()) {
      context.output(new PrintOutput(notificationName + " failed", OutputType.ERROR));
    }
  }
}
