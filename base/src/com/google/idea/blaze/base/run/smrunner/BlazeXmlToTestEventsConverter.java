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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.ErrorOrFailureOrSkipped;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestCase;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.sdkcompat.smrunner.OutputToGeneralTestEventsConverterAdapter;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor;
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent;
import com.intellij.execution.testframework.sm.runner.events.TestOutputEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import jetbrains.buildServer.messages.serviceMessages.TestSuiteStarted;

/** Converts blaze test runner xml logs to smRunner events. */
public class BlazeXmlToTestEventsConverter extends OutputToGeneralTestEventsConverterAdapter {

  private static final ErrorOrFailureOrSkipped NO_ERROR = new ErrorOrFailureOrSkipped();

  {
    NO_ERROR.message = "No message"; // cannot be null
  }

  private final BlazeTestResultFinderStrategy testResultFinderStrategy;

  public BlazeXmlToTestEventsConverter(
      String testFrameworkName,
      TestConsoleProperties testConsoleProperties,
      BlazeTestResultFinderStrategy testResultFinderStrategy) {
    super(testFrameworkName, testConsoleProperties);
    this.testResultFinderStrategy = testResultFinderStrategy;
  }

  @Override
  public void processTestSuites() {
    onStartTesting();
    getProcessor().onTestsReporterAttached();

    BlazeTestResults testResults = testResultFinderStrategy.findTestResults();
    if (testResults == null) {
      return;
    }
    for (Label label : testResults.perTargetResults.keySet()) {
      processTestSuites(label, testResults.perTargetResults.get(label));
    }
  }

  /** Process all test XML files from a single test target. */
  private void processTestSuites(Label label, Collection<BlazeTestResult> results) {
    List<File> outputFiles = new ArrayList<>();
    results.forEach(result -> outputFiles.addAll(result.getOutputXmlFiles()));

    if (noUsefulOutput(results, outputFiles)) {
      Optional<TestStatus> status =
          results.stream().map(BlazeTestResult::getTestStatus).findFirst();
      status.ifPresent(testStatus -> reportTargetWithoutOutputFiles(label, testStatus));
      return;
    }

    List<TestSuite> targetSuites = new ArrayList<>();
    for (File file : outputFiles) {
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
    Kind kind =
        results
            .stream()
            .map(BlazeTestResult::getTargetKind)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    BlazeTestEventsHandler eventsHandler =
        BlazeTestEventsHandler.getHandlerForTargetKindOrFallback(kind);
    TestSuite suite =
        targetSuites.size() == 1 ? targetSuites.get(0) : BlazeXmlSchema.mergeSuites(targetSuites);
    processTestSuite(getProcessor(), eventsHandler, kind, suite);
  }

  /** Return false if there's output XML which should be parsed. */
  private static boolean noUsefulOutput(
      Collection<BlazeTestResult> results, List<File> outputFiles) {
    if (outputFiles.isEmpty()) {
      return true;
    }
    TestStatus status =
        results.stream().map(BlazeTestResult::getTestStatus).findFirst().orElse(null);
    return status != null && BlazeTestResult.NO_USEFUL_OUTPUT.contains(status);
  }

  /**
   * If there are no output files, the test may have failed to build, or timed out. Provide a
   * suitable message in the test UI.
   */
  private void reportTargetWithoutOutputFiles(Label label, TestStatus status) {
    if (status == TestStatus.PASSED) {
      // Empty test targets do not produce output XML, yet technically pass. Ignore them.
      return;
    }
    GeneralTestEventsProcessor processor = getProcessor();
    TestSuiteStarted suiteStarted = new TestSuiteStarted(label.toString());
    processor.onSuiteStarted(new TestSuiteStartedEvent(suiteStarted, /*locationUrl=*/ null));
    String targetName = label.targetName().toString();
    processor.onTestStarted(new TestStartedEvent(targetName, /*locationUrl=*/ null));
    processor.onTestFailure(
        getTestFailedEvent(
            targetName,
            STATUS_EXPLANATIONS.get(status) + " See console output for details",
            /*content=*/ null,
            /*duration=*/ 0));
    processor.onTestFinished(new TestFinishedEvent(targetName, /*duration=*/ 0L));
    processor.onSuiteFinished(new TestSuiteFinishedEvent(label.toString()));
  }

  /** Status explanations for tests without output XML. */
  private static final ImmutableMap<TestStatus, String> STATUS_EXPLANATIONS =
      new ImmutableMap.Builder<TestStatus, String>()
          .put(TestStatus.TIMEOUT, "Test target timed out.")
          .put(TestStatus.INCOMPLETE, "Test output was incomplete.")
          .put(TestStatus.REMOTE_FAILURE, "Remote failure during test execution.")
          .put(TestStatus.FAILED_TO_BUILD, "Test target failed to build.")
          .put(TestStatus.TOOL_HALTED_BEFORE_TESTING, "Test target failed to build.")
          .put(TestStatus.NO_STATUS, "No output found for test target.")
          .build();

  private static void processTestSuite(
      GeneralTestEventsProcessor processor,
      BlazeTestEventsHandler eventsHandler,
      @Nullable Kind kind,
      TestSuite suite) {
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
      processTestSuite(processor, eventsHandler, kind, child);
    }
    for (TestSuite decorator : suite.testDecorators) {
      processTestSuite(processor, eventsHandler, kind, decorator);
    }
    for (TestCase test : suite.testCases) {
      processTestCase(processor, eventsHandler, kind, suite, test);
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
    return !test.failures.isEmpty() || !test.errors.isEmpty();
  }

  private static void processTestCase(
      GeneralTestEventsProcessor processor,
      BlazeTestEventsHandler eventsHandler,
      @Nullable Kind kind,
      TestSuite parent,
      TestCase test) {
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
      List<ErrorOrFailureOrSkipped> errors =
          !test.failures.isEmpty()
              ? test.failures
              : !test.errors.isEmpty() ? test.errors : ImmutableList.of(NO_ERROR);
      for (ErrorOrFailureOrSkipped err : errors) {
        processor.onTestFailure(
            getTestFailedEvent(displayName, err.message, err.content, parseTimeMillis(test.time)));
      }
    }
    processor.onTestFinished(new TestFinishedEvent(displayName, parseTimeMillis(test.time)));
  }

  private static TestFailedEvent getTestFailedEvent(
      String name, @Nullable String message, @Nullable String content, long duration) {
    return new TestFailedEvent(
        name, null, message, content, true, null, null, null, null, false, false, duration);
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
