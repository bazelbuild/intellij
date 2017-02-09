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
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** Utility methods for setting up the SM runner test UI. */
public class SmRunnerUtils {

  public static final String GENERIC_SUITE_PROTOCOL = "blaze:suite";
  public static final String GENERIC_TEST_PROTOCOL = "blaze:test";
  public static final String TEST_NAME_PARTS_SPLITTER = "::";
  public static final String BLAZE_FRAMEWORK = "blaze-test";

  public static SMTRunnerConsoleView getConsoleView(
      Project project,
      RunConfiguration configuration,
      Executor executor,
      BlazeTestEventsHandler eventsHandler) {
    SMTRunnerConsoleProperties properties =
        new BlazeTestConsoleProperties(configuration, executor, eventsHandler);
    SMTRunnerConsoleView console =
        (SMTRunnerConsoleView)
            SMTestRunnerConnectionUtil.createConsole(BLAZE_FRAMEWORK, properties);
    Disposer.register(project, console);
    console
        .getResultsViewer()
        .getTreeView()
        .getSelectionModel()
        .setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    return console;
  }

  public static DefaultExecutionResult attachRerunFailedTestsAction(DefaultExecutionResult result) {
    ExecutionConsole console = result.getExecutionConsole();
    if (!(console instanceof SMTRunnerConsoleView)) {
      return result;
    }
    SMTRunnerConsoleView smConsole = (SMTRunnerConsoleView) console;
    TestConsoleProperties consoleProperties = smConsole.getProperties();
    if (!(consoleProperties instanceof BlazeTestConsoleProperties)) {
      return result;
    }
    BlazeTestConsoleProperties properties = (BlazeTestConsoleProperties) consoleProperties;
    AbstractRerunFailedTestsAction action = properties.createRerunFailedTestsAction(smConsole);
    if (action != null) {
      action.init(properties);
      action.setModelProvider(smConsole::getResultsViewer);
      result.setRestartActions(action);
    }
    return result;
  }

  public static List<Location<?>> getSelectedSmRunnerTreeElements(ConfigurationContext context) {
    SMTRunnerTestTreeView treeView =
        SMTRunnerTestTreeView.SM_TEST_RUNNER_VIEW.getData(context.getDataContext());
    if (treeView == null) {
      return ImmutableList.of();
    }
    TreePath[] paths = treeView.getSelectionPaths();
    if (paths == null || paths.length == 0) {
      return ImmutableList.of();
    }
    return Arrays.stream(paths)
        .map((path) -> toLocation(context.getProject(), treeView, path))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Nullable
  private static Location<?> toLocation(
      Project project, SMTRunnerTestTreeView treeView, TreePath path) {
    if (treeView.isPathSelected(path.getParentPath())) {
      return null;
    }
    SMTestProxy test = treeView.getSelectedTest(path);
    return test != null ? test.getLocation(project, GlobalSearchScope.allScope(project)) : null;
  }
}
