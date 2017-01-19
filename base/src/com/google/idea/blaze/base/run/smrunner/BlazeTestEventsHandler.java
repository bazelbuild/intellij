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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import javax.annotation.Nullable;

/** Language-specific handling of SM runner test protocol */
public abstract class BlazeTestEventsHandler {

  public final String frameworkName;
  public final File testOutputXml;

  protected BlazeTestEventsHandler(String frameworkName) {
    this.frameworkName = frameworkName;
    this.testOutputXml = generateTempTestXmlFile();
  }

  /**
   * Blaze/Bazel flags required for test UI.<br>
   * Forces local test execution, without sharding, and sets the output test xml path.
   */
  public ImmutableList<String> getBlazeFlags() {
    return ImmutableList.of(
        "--test_env=XML_OUTPUT_FILE=" + testOutputXml,
        "--test_sharding_strategy=disabled",
        "--runs_per_test=1",
        "--flaky_test_attempts=1",
        "--test_strategy=local");
  }

  public abstract SMTestLocator getTestLocator();

  /**
   * The --test_filter flag passed to blaze to rerun the given tests.
   *
   * @return null if no filter can be constructed for these tests.
   */
  @Nullable
  public abstract String getTestFilter(Project project, List<AbstractTestProxy> failedTests);

  @Nullable
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return new BlazeRerunFailedTestsAction(this, consoleView);
  }

  /** Converts the testsuite name in the blaze test XML to a user-friendly format */
  public String suiteDisplayName(String rawName) {
    return rawName;
  }

  /** Converts the testcase name in the blaze test XML to a user-friendly format */
  public String testDisplayName(String rawName) {
    return rawName;
  }

  public String suiteLocationUrl(String name) {
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  public String testLocationUrl(String name, @Nullable String className) {
    String base = SmRunnerUtils.GENERIC_TEST_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
    if (Strings.isNullOrEmpty(className)) {
      return base;
    }
    return base + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER + className;
  }

  private static File generateTempTestXmlFile() {
    try {
      File file = Files.createTempFile("blazeTest", ".xml").toFile();
      file.deleteOnExit();
      return file;

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
