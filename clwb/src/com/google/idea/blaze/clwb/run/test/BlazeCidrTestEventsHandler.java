/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.run.test;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.URLUtil;
import com.jetbrains.cidr.execution.testing.OCGoogleTestLocationProvider;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;

/** Provides C/C++ specific methods needed by the SM-runner test UI. */
public class BlazeCidrTestEventsHandler extends BlazeTestEventsHandler {

  @Override
  protected EnumSet<Kind> handledKinds() {
    return EnumSet.of(Kind.CC_TEST);
  }

  @Override
  public SMTestLocator getTestLocator() {
    return OCGoogleTestLocationProvider.INSTANCE;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<Location<?>> testLocations) {
    List<String> filters = new ArrayList<>();
    for (Location<?> location : testLocations) {
      BlazeCidrTestTarget target = BlazeCidrTestTarget.findTestObject(location.getPsiElement());
      if (target != null && target.testFilter != null) {
        filters.add(target.testFilter);
      }
    }
    if (filters.isEmpty()) {
      return null;
    }
    return String.format("%s=%s", BlazeFlags.TEST_FILTER, Joiner.on(':').join(filters));
  }

  @Override
  public String suiteLocationUrl(@Nullable Kind kind, String name) {
    return OCGoogleTestLocationProvider.PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  @Override
  public String testLocationUrl(
      @Nullable Kind kind, String parentSuite, String name, @Nullable String className) {
    if (className == null) {
      return OCGoogleTestLocationProvider.PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
    }
    return OCGoogleTestLocationProvider.PROTOCOL
        + URLUtil.SCHEME_SEPARATOR
        + className
        + "."
        + name;
  }
}
