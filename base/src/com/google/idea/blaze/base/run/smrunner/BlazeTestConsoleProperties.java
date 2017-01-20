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
package com.google.idea.blaze.base.run.smrunner;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import javax.annotation.Nullable;

/** Integrates blaze test results with the SM-runner test UI. */
public class BlazeTestConsoleProperties extends SMTRunnerConsoleProperties
    implements SMCustomMessagesParsing {

  private final BlazeTestEventsHandler eventsHandler;

  public BlazeTestConsoleProperties(
      RunConfiguration runConfiguration, Executor executor, BlazeTestEventsHandler eventsHandler) {
    super(runConfiguration, eventsHandler.frameworkName, executor);
    this.eventsHandler = eventsHandler;
  }

  @Override
  public OutputToGeneralTestEventsConverter createTestEventsConverter(
      String framework, TestConsoleProperties consoleProperties) {
    return new BlazeXmlToTestEventsConverter(framework, consoleProperties, eventsHandler);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return eventsHandler.getTestLocator();
  }

  @Nullable
  @Override
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return eventsHandler.createRerunFailedTestsAction(consoleView);
  }
}
