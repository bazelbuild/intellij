/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Combines multiple language-specific handlers (e.g. to handle test_suite targets). */
public class BlazeCompositeTestEventsHandler extends BlazeTestEventsHandler {

  private static ImmutableMap<Kind, BlazeTestEventsHandler> collectHandlers() {
    Map<Kind, BlazeTestEventsHandler> map = new HashMap<>();
    for (BlazeTestEventsHandler handler : BlazeTestEventsHandler.EP_NAME.getExtensions()) {
      if (handler instanceof BlazeCompositeTestEventsHandler) {
        continue;
      }
      for (Kind kind : handler.handledKinds()) {
        // earlier handlers get priority.
        map.putIfAbsent(kind, handler);
      }
    }
    return Maps.immutableEnumMap(map);
  }

  private static ImmutableMap<Kind, BlazeTestEventsHandler> handlers;

  private static ImmutableMap<Kind, BlazeTestEventsHandler> getHandlers() {
    if (handlers == null) {
      handlers = collectHandlers();
    }
    return handlers;
  }

  @Override
  public boolean handlesTargetKind(@Nullable Kind kind) {
    // composite handler specifically exists to handle test-suites and multi-target blaze
    // invocations, so must handle targets without a kind.
    return kind == null || kind == Kind.TEST_SUITE || handledKinds().contains(kind);
  }

  @Override
  protected EnumSet<Kind> handledKinds() {
    ImmutableSet<Kind> handledKinds = getHandlers().keySet();
    return !handledKinds.isEmpty() ? EnumSet.copyOf(handledKinds) : EnumSet.noneOf(Kind.class);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return new CompositeSMTestLocator(
        ImmutableList.copyOf(
            getHandlers()
                .values()
                .stream()
                .map(BlazeTestEventsHandler::getTestLocator)
                .collect(Collectors.toList())));
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    // We make no attempt to support re-running a subset of tests for test_suites or target patterns
    return null;
  }

  @Nullable
  @Override
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return null;
  }

  @Override
  public boolean ignoreSuite(@Nullable Kind kind, TestSuite suite) {
    BlazeTestEventsHandler handler = kind != null ? getHandlers().get(kind) : null;
    return handler != null ? handler.ignoreSuite(kind, suite) : super.ignoreSuite(kind, suite);
  }

  /** Converts the testsuite name in the blaze test XML to a user-friendly format */
  @Override
  public String suiteDisplayName(@Nullable Kind kind, String rawName) {
    BlazeTestEventsHandler handler = kind != null ? getHandlers().get(kind) : null;
    return handler != null
        ? handler.suiteDisplayName(kind, rawName)
        : super.suiteDisplayName(kind, rawName);
  }

  /** Converts the testcase name in the blaze test XML to a user-friendly format */
  @Override
  public String testDisplayName(@Nullable Kind kind, String rawName) {
    BlazeTestEventsHandler handler = kind != null ? getHandlers().get(kind) : null;
    return handler != null
        ? handler.testDisplayName(kind, rawName)
        : super.testDisplayName(kind, rawName);
  }

  @Override
  public String suiteLocationUrl(@Nullable Kind kind, String name) {
    BlazeTestEventsHandler handler = kind != null ? getHandlers().get(kind) : null;
    return handler != null
        ? handler.suiteLocationUrl(kind, name)
        : super.suiteLocationUrl(kind, name);
  }

  @Override
  public String testLocationUrl(
      @Nullable Kind kind, String parentSuite, String name, @Nullable String className) {
    BlazeTestEventsHandler handler = getHandlers().get(kind);
    return handler != null
        ? handler.testLocationUrl(kind, parentSuite, name, className)
        : super.testLocationUrl(kind, parentSuite, name, className);
  }
}
