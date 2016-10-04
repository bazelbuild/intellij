/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.ConsoleProvider;
import com.android.tools.idea.run.testing.AndroidTestConsoleProperties;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

/** Console provider for android_test */
class AndroidTestConsoleProvider implements ConsoleProvider {
  private final Project project;
  private final RunConfiguration runConfiguration;
  private final BlazeAndroidTestRunConfigurationState configState;

  AndroidTestConsoleProvider(
      Project project,
      RunConfiguration runConfiguration,
      BlazeAndroidTestRunConfigurationState configState) {
    this.project = project;
    this.runConfiguration = runConfiguration;
    this.configState = configState;
  }

  @NotNull
  @Override
  public ConsoleView createAndAttach(
      @NotNull Disposable parent, @NotNull ProcessHandler handler, @NotNull Executor executor)
      throws ExecutionException {
    if (!configState.isRunThroughBlaze()) {
      return getStockConsoleProvider().createAndAttach(parent, handler, executor);
    }
    ConsoleView console =
        TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    console.attachToProcess(handler);
    return console;
  }

  private ConsoleProvider getStockConsoleProvider() {
    return (parent, handler, executor) -> {
      AndroidTestConsoleProperties properties =
          new AndroidTestConsoleProperties(runConfiguration, executor);
      ConsoleView consoleView =
          SMTestRunnerConnectionUtil.createAndAttachConsole("Android", handler, properties);
      Disposer.register(parent, consoleView);
      return consoleView;
    };
  }
}
