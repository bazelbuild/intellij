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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import javax.annotation.Nullable;

/** Maps method and class annotations to our test size enumeration. */
public class TestSizeAnnotationMap {
  private static final ImmutableMap<String, TestSize> ANNOTATION_TO_TEST_SIZE =
      ImmutableMap.<String, TestSize>builder()
          .put("com.google.testing.testsize.SmallTest", TestSize.SMALL)
          .put("com.google.testing.testsize.MediumTest", TestSize.MEDIUM)
          .put("com.google.testing.testsize.LargeTest", TestSize.LARGE)
          .put("com.google.testing.testsize.EnormousTest", TestSize.ENORMOUS)
          .build();

  @Nullable
  public static TestSize getTestSize(PsiMethod psiMethod) {
    PsiAnnotation[] annotations = psiMethod.getModifierList().getAnnotations();
    TestSize testSize = getTestSize(annotations);
    if (testSize != null) {
      return testSize;
    }
    return getTestSize(psiMethod.getContainingClass());
  }

  @Nullable
  public static TestSize getTestSize(PsiClass psiClass) {
    PsiModifierList psiModifierList = psiClass.getModifierList();
    if (psiModifierList == null) {
      return null;
    }
    PsiAnnotation[] annotations = psiModifierList.getAnnotations();
    TestSize testSize = getTestSize(annotations);
    if (testSize == null) {
      return null;
    }
    return testSize;
  }

  @Nullable
  private static TestSize getTestSize(PsiAnnotation[] annotations) {
    for (PsiAnnotation annotation : annotations) {
      String qualifiedName = annotation.getQualifiedName();
      TestSize testSize = ANNOTATION_TO_TEST_SIZE.get(qualifiedName);
      if (testSize != null) {
        return testSize;
      }
    }
    return null;
  }
}
