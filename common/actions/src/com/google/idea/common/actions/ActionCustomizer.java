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
package com.google.idea.common.actions;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import javax.annotation.Nullable;

/**
 * {@link ActionCustomizer} provides a way to register callbacks that need to be run after {@link
 * ActionManager} is initialized.
 *
 * <p>#api193. Starting 2020.1, plugins can use the actionConfigurationCustomizer extension.
 */
public class ActionCustomizer {
  /**
   * Register the given runnable to be invoked *after* the {@link ActionManager} has been
   * initialized.
   */
  public static void newCustomizerFor(Runnable runnable) {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(
        AppLifecycleListener.TOPIC,
        new AppLifecycleListener() {
          @Override
          public void appStarting(@Nullable Project projectFromCommandLine) {
            connection.disconnect();

            // In v201, creation of the ActionManager will trigger this message topic again, so
            // we invoke the runnables in a separate thread to avoid a circular dependency.
            ApplicationManager.getApplication().executeOnPooledThread(runnable);
          }
        });
  }

  private ActionCustomizer() {}
}
