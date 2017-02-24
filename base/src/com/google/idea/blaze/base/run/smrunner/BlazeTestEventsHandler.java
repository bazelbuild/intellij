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
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.google.idea.blaze.base.run.targetfinder.TargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.URLUtil;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;

/** Language-specific handling of SM runner test protocol */
public abstract class BlazeTestEventsHandler {

  static final ExtensionPointName<BlazeTestEventsHandler> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeTestEventsHandler");

  /**
   * Blaze/Bazel flags required for test UI.<br>
   * Forces local test execution, without retries.
   */
  public static ImmutableList<String> getBlazeFlags(Project project) {
    ImmutableList.Builder<String> flags =
        ImmutableList.<String>builder().add("--runs_per_test=1", "--flaky_test_attempts=1");
    if (Blaze.getBuildSystem(project) == BuildSystem.Blaze) {
      flags.add("--test_strategy=local");
    }
    if (Blaze.getBuildSystem(project) == BuildSystem.Bazel) {
      flags.add("--test_sharding_strategy=disabled");
    }
    return flags.build();
  }

  @Nullable
  public static BlazeTestEventsHandler getHandlerForTarget(
      Project project, TargetExpression target) {
    Kind kind = getKindForTarget(project, target);
    for (BlazeTestEventsHandler handler : EP_NAME.getExtensions()) {
      if (handler.handlesTargetKind(kind)) {
        return handler;
      }
    }
    return null;
  }

  @Nullable
  private static Kind getKindForTarget(Project project, TargetExpression target) {
    if (!(target instanceof Label)) {
      return null;
    }
    TargetIdeInfo targetInfo = TargetFinder.getInstance().targetForLabel(project, (Label) target);
    return targetInfo != null ? targetInfo.kind : null;
  }

  public boolean handlesTargetKind(@Nullable Kind kind) {
    return handledKinds().contains(kind);
  }

  protected abstract EnumSet<Kind> handledKinds();

  public abstract SMTestLocator getTestLocator();

  /**
   * The --test_filter flag passed to blaze to rerun the given tests.
   *
   * @return null if no filter can be constructed for these tests.
   */
  @Nullable
  public abstract String getTestFilter(Project project, List<Location<?>> testLocations);

  @Nullable
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return new BlazeRerunFailedTestsAction(this, consoleView);
  }

  /** Converts the testsuite name in the blaze test XML to a user-friendly format */
  public String suiteDisplayName(@Nullable Kind kind, String rawName) {
    return rawName;
  }

  /** Converts the testcase name in the blaze test XML to a user-friendly format */
  public String testDisplayName(@Nullable Kind kind, String rawName) {
    return rawName;
  }

  public String suiteLocationUrl(@Nullable Kind kind, String name) {
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  public String testLocationUrl(
      @Nullable Kind kind, String parentSuite, String name, @Nullable String className) {
    String base = SmRunnerUtils.GENERIC_TEST_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
    if (Strings.isNullOrEmpty(className)) {
      return base;
    }
    return base + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER + className;
  }

  /** Whether to skip logging a {@link TestSuite}. */
  public boolean ignoreSuite(@Nullable Kind kind, TestSuite suite) {
    // by default only include innermost 'testsuite' elements
    return !suite.testSuites.isEmpty();
  }
}
