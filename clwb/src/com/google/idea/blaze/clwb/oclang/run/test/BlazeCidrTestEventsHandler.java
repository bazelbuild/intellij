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
package com.google.idea.blaze.clwb.oclang.run.test;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Provides C/C++ specific methods needed by the SM-runner test UI. */
public class BlazeCidrTestEventsHandler implements BlazeTestEventsHandler {

  @Override
  public boolean handlesKind(@Nullable Kind kind) {
    return kind != null
        && kind.hasLanguage(LanguageClass.C)
        && kind.getRuleType().equals(RuleType.TEST);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return BlazeCppTestLocator.INSTANCE;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    List<String> filters =
        testLocations.stream()
            .map(l -> GoogleTestLocation.findGoogleTest(l, project))
            .map(l -> l != null ? l.getTestFilter() : null)
            .filter(Objects::nonNull)
            .collect(toImmutableList());
    if (filters.isEmpty()) {
      return null;
    }
    return String.format(
        "%s=%s",
        BlazeFlags.TEST_FILTER, BlazeParametersListUtil.encodeParam(Joiner.on(':').join(filters)));
  }
}
