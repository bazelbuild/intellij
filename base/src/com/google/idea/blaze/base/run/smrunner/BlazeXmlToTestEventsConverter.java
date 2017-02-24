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

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.ErrorOrFailureOrSkipped;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestCase;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.run.testlogs.BlazeTestXmlFinderStrategy;
import com.google.idea.sdkcompat.smrunner.SmRunnerCompatUtils;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;

/** Converts blaze test runner xml logs to smRunner events. */
public class BlazeXmlToTestEventsConverter extends OutputToGeneralTestEventsConverter {

  private static final ErrorOrFailureOrSkipped NO_ERROR = new ErrorOrFailureOrSkipped();

  {
    NO_ERROR.message = "No message"; // cannot be null
  }

  private final Project project;
  private final BlazeTestEventsHandler eventsHandler;

  public BlazeXmlToTestEventsConverter(
      String testFrameworkName,
      TestConsoleProperties testConsoleProperties,
      BlazeTestEventsHandler eventsHandler) {
    super(testFrameworkName, testConsoleProperties);
    this.project = testConsoleProperties.getProject();
    this.eventsHandler = eventsHandler;
  }

  @Override
  protected boolean processServiceMessages(
      String s, Key key, ServiceMessageVisitor serviceMessageVisitor) throws ParseException {
    return super.processServiceMessages(s, key, serviceMessageVisitor);
  }

  @Override
  public void process(String text, Key outputType) {
    super.process(text, outputType);
  }

  @Override
  public void dispose() {
    super.dispose();
  }

  @Override
  public void flushBufferBeforeTerminating() {
    super.flushBufferBeforeTerminating();
    onStartTesting();
    getProcessor().onTestsReporterAttached();

    ImmutableMultimap<Label, File> xmlFiles =
        BlazeTestXmlFinderStrategy.locateTestXmlFiles(project);
    for (Label label : xmlFiles.keySet()) {
      processTestSuites(label, xmlFiles.get(label));
    }
  }

  /** Process all test XML files from a single test target. */
  private void processTestSuites(Label label, Collection<File> files) {
    Kind kind = getKind(project, label);
    List<TestSuite> targetSuites = new ArrayList<>();
    for (File file : files) {
      try (InputStream input = new FileInputStream(file)) {
        targetSuites.add(BlazeXmlSchema.parse(input));
      } catch (Exception e) {
        // ignore parsing errors -- most common cause is user cancellation, which we can't easily
        // recognize.
      }
    }
    if (targetSuites.isEmpty()) {
      return;
    }
    TestSuite suite =
        targetSuites.size() == 1 ? targetSuites.get(0) : BlazeXmlSchema.mergeSuites(targetSuites);
    processTestSuite(getProcessor(), kind, suite);
  }

  @Nullable
  private static Kind getKind(Project project, Label label) {
    TargetIdeInfo target = TargetFinder.getInstance().targetForLabel(project, label);
    return target != null ? target.kind : null;
  }

  private void processTestSuite(
      GeneralTestEventsProcessor processor, @Nullable Kind kind, TestSuite suite) {
    if (!hasRunChild(suite)) {
      return;
    }
    // only include the innermost 'testsuite' element
    boolean logSuite = !eventsHandler.ignoreSuite(kind, suite);
    if (suite.name != null && logSuite) {
      TestSuiteStarted suiteStarted =
          new TestSuiteStarted(eventsHandler.suiteDisplayName(kind, suite.name));
      String locationUrl = eventsHandler.suiteLocationUrl(kind, suite.name);
      processor.onSuiteStarted(new TestSuiteStartedEvent(suiteStarted, locationUrl));
    }

    for (TestSuite child : suite.testSuites) {
      processTestSuite(processor, kind, child);
    }
    for (TestSuite decorator : suite.testDecorators) {
      processTestSuite(processor, kind, decorator);
    }
    for (TestCase test : suite.testCases) {
      processTestCase(processor, kind, suite, test);
    }

    if (suite.sysOut != null) {
      processor.onUncapturedOutput(suite.sysOut, ProcessOutputTypes.STDOUT);
    }
    if (suite.sysErr != null) {
      processor.onUncapturedOutput(suite.sysErr, ProcessOutputTypes.STDERR);
    }

    if (suite.name != null && logSuite) {
      processor.onSuiteFinished(
          new TestSuiteFinishedEvent(eventsHandler.suiteDisplayName(kind, suite.name)));
    }
  }

  /**
   * Does the test suite have at least one child which wasn't skipped? <br>
   * This prevents spurious warnings from entirely filtered test classes.
   */
  private static boolean hasRunChild(TestSuite suite) {
    for (TestSuite child : suite.testSuites) {
      if (hasRunChild(child)) {
        return true;
      }
    }
    for (TestSuite child : suite.testDecorators) {
      if (hasRunChild(child)) {
        return true;
      }
    }
    for (TestCase test : suite.testCases) {
      if (wasRun(test) && !isIgnored(test)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isCancelled(TestCase test) {
    return "interrupted".equalsIgnoreCase(test.result) || "cancelled".equalsIgnoreCase(test.result);
  }

  private static boolean wasRun(TestCase test) {
    // 'status' is not always set. In cases where it's not, tests which aren't run have a 0 runtime.
    if (test.status != null) {
      return test.status.equals("run");
    }
    return parseTimeMillis(test.time) != 0;
  }

  private static boolean isIgnored(TestCase test) {
    if (test.skipped != null) {
      return true;
    }
    return "suppressed".equalsIgnoreCase(test.result)
        || "skipped".equalsIgnoreCase(test.result)
        || "filtered".equalsIgnoreCase(test.result);
  }

  private static boolean isFailed(TestCase test) {
    return test.failure != null || test.error != null;
  }

  private void processTestCase(
      GeneralTestEventsProcessor processor, @Nullable Kind kind, TestSuite parent, TestCase test) {
    if (test.name == null || !wasRun(test) || isCancelled(test)) {
      return;
    }
    String displayName = eventsHandler.testDisplayName(kind, test.name);
    String locationUrl =
        eventsHandler.testLocationUrl(kind, parent.name, test.name, test.classname);
    processor.onTestStarted(new TestStartedEvent(displayName, locationUrl));

    if (test.sysOut != null) {
      processor.onTestOutput(new TestOutputEvent(displayName, test.sysOut, true));
    }
    if (test.sysErr != null) {
      processor.onTestOutput(new TestOutputEvent(displayName, test.sysErr, true));
    }

    if (isIgnored(test)) {
      ErrorOrFailureOrSkipped err = test.skipped != null ? test.skipped : NO_ERROR;
      processor.onTestIgnored(new TestIgnoredEvent(displayName, err.message, err.content));
    } else if (isFailed(test)) {
      ErrorOrFailureOrSkipped err =
          test.failure != null ? test.failure : test.error != null ? test.error : NO_ERROR;
      processor.onTestFailure(
          SmRunnerCompatUtils.getTestFailedEvent(
              displayName, err.message, err.content, parseTimeMillis(test.time)));
    }
    processor.onTestFinished(new TestFinishedEvent(displayName, parseTimeMillis(test.time)));
  }

  private static long parseTimeMillis(@Nullable String time) {
    if (time == null) {
      return -1;
    }
    // if the number contains a decimal point, it's a value in seconds. Otherwise in milliseconds.
    try {
      if (time.contains(".")) {
        return Math.round(Float.parseFloat(time) * 1000);
      }
      return Long.parseLong(time);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
