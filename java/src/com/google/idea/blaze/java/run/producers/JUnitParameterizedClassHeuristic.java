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
package com.google.idea.blaze.java.run.producers;

import com.google.auto.value.AutoValue;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** A heuristic to recognize JUnit test runner classes which have parameterized test cases. */
public interface JUnitParameterizedClassHeuristic {

  String STANDARD_JUNIT_TEST_SUFFIX = "(\\[.+\\])?";
  String USER_SPECIFIED_TEST_SUFFIX = ".*";

  /** Information about the parameterized test. */
  @AutoValue
  abstract class ParameterizedTestInfo {
    public abstract String parameterizedRunnerClass();

    public abstract String testSuffixRegex();

    public static ParameterizedTestInfo create(
        String parameterizedRunnerClass, String testSuffixRegex) {
      return new AutoValue_JUnitParameterizedClassHeuristic_ParameterizedTestInfo(
          parameterizedRunnerClass, testSuffixRegex);
    }
  }

  ExtensionPointName<JUnitParameterizedClassHeuristic> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.JUnitParameterizedClassHeuristic");

  @Nullable
  static ParameterizedTestInfo getParameterizedTestInfo(PsiClass psiClass) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(heuristic -> heuristic.getTestInfo(psiClass))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  ParameterizedTestInfo getTestInfo(PsiClass psiClass);
}
