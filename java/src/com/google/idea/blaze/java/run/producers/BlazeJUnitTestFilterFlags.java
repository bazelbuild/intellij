/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.java.run.producers;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.command.BlazeFlags;
import java.util.Collection;
import javax.annotation.Nullable;

/** Utilities for building test filter flags for JUnit tests. */
public final class BlazeJUnitTestFilterFlags {

  /** A version of JUnit to generate test filter flags for. */
  public enum JUnitVersion {
    JUNIT_3,
    JUNIT_4
  }

  public static String testFilterFlagForClass(String className, JUnitVersion jUnitVersion) {
    return testFilterFlagForClassAndMethod(className, null, jUnitVersion);
  }

  public static String testFilterFlagForClassAndMethod(
      String className, @Nullable String methodName, JUnitVersion jUnitVersion) {
    StringBuilder output = new StringBuilder(BlazeFlags.TEST_FILTER);
    output.append('=');
    output.append(className);

    if (!Strings.isNullOrEmpty(methodName)) {
      output.append('#');
      output.append(methodName);
      // JUnit 4 test filters are regexes, and must be terminated to avoid matching
      // unintended classes/methods. JUnit 3 test filters do not need or support this syntax.
      if (jUnitVersion == JUnitVersion.JUNIT_4) {
        output.append('$');
      }
    } else if (jUnitVersion == JUnitVersion.JUNIT_4) {
      output.append('#');
    }

    return output.toString();
  }

  public static String testFilterFlagForClassAndMethods(
      String className, Collection<String> methodNames, JUnitVersion jUnitVersion) {
    if (methodNames.size() == 0) {
      return testFilterFlagForClass(className, jUnitVersion);
    } else if (methodNames.size() == 1) {
      return testFilterFlagForClassAndMethod(
          className, methodNames.iterator().next(), jUnitVersion);
    }
    String methodNamePattern;
    if (jUnitVersion == JUnitVersion.JUNIT_4) {
      methodNamePattern = String.format("(%s)", String.join("|", methodNames));
    } else {
      methodNamePattern = String.join(",", methodNames);
    }
    return testFilterFlagForClassAndMethod(className, methodNamePattern, jUnitVersion);
  }

  private BlazeJUnitTestFilterFlags() {}
}
