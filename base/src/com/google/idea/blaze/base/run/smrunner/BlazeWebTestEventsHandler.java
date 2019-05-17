/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.URLUtil;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Encodes everything directly into the URL, will be decoded and re-encoded for the appropriate
 * underlying {@link BlazeTestEventsHandler}.
 */
public class BlazeWebTestEventsHandler implements BlazeTestEventsHandler {
  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind == RuleTypes.WEB_TEST.getKind();
  }

  @Override
  @Nullable
  public SMTestLocator getTestLocator() {
    return BlazeWebTestLocator.INSTANCE;
  }

  /**
   * Just runs through all other {@link * BlazeTestEventsHandler#getTestFilter}s and combine their
   * results, though only one handler should return anything.
   */
  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    String filter =
        BlazeTestEventsHandler.EP_NAME
            .extensions()
            .filter(e -> e != this)
            .map(e -> e.getTestFilter(project, testLocations))
            .filter(Objects::nonNull)
            .distinct()
            .filter(f -> f.startsWith(BlazeFlags.TEST_FILTER + '='))
            .map(f -> f.substring(BlazeFlags.TEST_FILTER.length() + 1))
            .filter(f -> !f.isEmpty())
            .reduce((a, b) -> a + "|" + b)
            .orElse(null);
    return filter != null ? String.format("%s=%s", BlazeFlags.TEST_FILTER, filter) : null;
  }

  @Override
  public String testLocationUrl(
      Label label,
      @Nullable Kind kind,
      String parentSuite,
      String name,
      @Nullable String className) {
    return SmRunnerUtils.GENERIC_TEST_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + label
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + parentSuite
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + Strings.nullToEmpty(className);
  }

  @Override
  public String suiteLocationUrl(Label label, @Nullable Kind kind, String name) {
    return SmRunnerUtils.GENERIC_SUITE_PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + label
        + SmRunnerUtils.TEST_NAME_PARTS_SPLITTER
        + name;
  }
}
