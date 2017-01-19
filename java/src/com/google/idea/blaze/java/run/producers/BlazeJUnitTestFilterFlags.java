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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.MultiMap;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Utilities for building test filter flags for JUnit tests. */
public final class BlazeJUnitTestFilterFlags {

  /** A version of JUnit to generate test filter flags for. */
  public enum JUnitVersion {
    JUNIT_3,
    JUNIT_4
  }

  /**
   * Builds the JUnit test filter corresponding to the given class.<br>
   * Returns null if no class name can be found.
   */
  @Nullable
  public static String testFilterForClass(PsiClass psiClass) {
    return testFilterForClassAndMethods(psiClass, ImmutableList.of());
  }

  /**
   * Builds the JUnit test filter corresponding to the given class and methods.<br>
   * Returns null if no class name can be found.
   */
  @Nullable
  public static String testFilterForClassAndMethods(
      PsiClass psiClass, Collection<PsiMethod> methods) {
    JUnitVersion version =
        JUnitUtil.isJUnit4TestClass(psiClass) ? JUnitVersion.JUNIT_4 : JUnitVersion.JUNIT_3;
    return testFilterForClassAndMethods(psiClass, version, methods);
  }

  @Nullable
  public static String testFilterForClassesAndMethods(
      MultiMap<PsiClass, PsiMethod> methodsPerClass) {
    // Note: this could be incorrect if there are no JUnit4 classes in this sample, but some in the
    // java_test target they're run from.
    JUnitVersion version =
        hasJUnit4Test(methodsPerClass.keySet()) ? JUnitVersion.JUNIT_4 : JUnitVersion.JUNIT_3;
    return testFilterForClassesAndMethods(methodsPerClass, version);
  }

  @Nullable
  public static String testFilterForClassesAndMethods(
      MultiMap<PsiClass, PsiMethod> methodsPerClass, JUnitVersion version) {
    StringBuilder output = new StringBuilder();
    for (Entry<PsiClass, Collection<PsiMethod>> entry : methodsPerClass.entrySet()) {
      String filter = testFilterForClassAndMethods(entry.getKey(), version, entry.getValue());
      if (filter != null) {
        output.append(filter);
      }
    }
    return Strings.emptyToNull(output.toString());
  }

  private static boolean hasJUnit4Test(Collection<PsiClass> classes) {
    for (PsiClass psiClass : classes) {
      if (JUnitUtil.isJUnit4TestClass(psiClass)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds the JUnit test filter corresponding to the given class and methods.<br>
   * Returns null if no class name can be found.
   */
  @Nullable
  private static String testFilterForClassAndMethods(
      PsiClass psiClass, JUnitVersion version, Collection<PsiMethod> methods) {
    String className = psiClass.getQualifiedName();
    if (className == null) {
      return null;
    }
    // Sort so multiple configurations created with different selection orders are the same.
    List<String> methodNames =
        methods.stream().map(PsiMethod::getName).sorted().collect(Collectors.toList());
    return testFilterForClassAndMethods(className, methodNames, version, isParameterized(psiClass));
  }

  private static boolean isParameterized(PsiClass testClass) {
    return PsiMemberParameterizedLocation.getParameterizedLocation(testClass, null) != null;
  }

  /**
   * Builds the blaze test_filter flag for JUnit tests. Excludes the "--test_filter" component of
   * the flag, so that multiple test classes can be combined.
   */
  @VisibleForTesting
  static String testFilterForClassAndMethods(
      String className,
      List<String> methodNames,
      JUnitVersion jUnitVersion,
      boolean parameterized) {
    StringBuilder output = new StringBuilder(className);
    String methodNamePattern = concatenateMethodNames(methodNames, jUnitVersion);
    if (Strings.isNullOrEmpty(methodNamePattern)) {
      if (jUnitVersion == JUnitVersion.JUNIT_4) {
        output.append('#');
      }
      return output.toString();
    }
    output.append('#').append(methodNamePattern);
    // JUnit 4 test filters are regexes, and must be terminated to avoid matching
    // unintended classes/methods. JUnit 3 test filters do not need or support this syntax.
    if (jUnitVersion == JUnitVersion.JUNIT_3) {
      return output.toString();
    }
    // parameterized tests include their parameters between brackets after the method name
    if (parameterized) {
      output.append("(\\[.+\\])?");
    }
    output.append('$');
    return output.toString();
  }

  @Nullable
  private static String concatenateMethodNames(
      List<String> methodNames, JUnitVersion jUnitVersion) {
    if (methodNames.isEmpty()) {
      return null;
    }
    if (methodNames.size() == 1) {
      return methodNames.get(0);
    }
    return jUnitVersion == JUnitVersion.JUNIT_4
        ? String.format("(%s)", String.join("|", methodNames))
        : String.join(",", methodNames);
  }

  private BlazeJUnitTestFilterFlags() {}
}
