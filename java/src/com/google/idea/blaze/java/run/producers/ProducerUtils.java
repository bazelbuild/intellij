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
package com.google.idea.blaze.java.run.producers;

import com.intellij.execution.Location;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Utility methods for java test run configuration producers. */
public class ProducerUtils {
  @Nullable
  public static Location<PsiMethod> getMethodLocation(@NotNull Location contextLocation) {
    Location<PsiMethod> methodLocation = getTestMethod(contextLocation);
    if (methodLocation == null) {
      return null;
    }

    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      PsiClass containingClass =
          ((PsiMemberParameterizedLocation) contextLocation).getContainingClass();
      if (containingClass != null) {
        methodLocation =
            MethodLocation.elementInClass(methodLocation.getPsiElement(), containingClass);
      }
    }
    return methodLocation;
  }

  @Nullable
  public static Location<PsiMethod> getTestMethod(final Location<?> location) {
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false);
        iterator.hasNext();
        ) {
      final Location<PsiMethod> methodLocation = iterator.next();
      if (JUnitUtil.isTestMethod(methodLocation, false)) {
        return methodLocation;
      }
    }
    return null;
  }

  /** For any test classes with nested inner test classes, also add the inner classes to the set. */
  static Set<PsiClass> includeInnerTestClasses(Set<PsiClass> testClasses) {
    Set<PsiClass> result = new HashSet<>(testClasses);
    for (PsiClass psiClass : testClasses) {
      result.addAll(getInnerTestClasses(psiClass));
    }
    return result;
  }

  static Set<PsiClass> getInnerTestClasses(PsiClass psiClass) {
    return Arrays.stream(psiClass.getInnerClasses())
        .filter(JUnitUtil::isTestClass)
        .collect(Collectors.toSet());
  }
}
