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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;

/** A heuristic to recognize JUnit test runner classes which have parameterized test cases. */
public interface JUnitParameterizedClassHeuristic {

  ExtensionPointName<JUnitParameterizedClassHeuristic> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.JUnitParameterizedClassHeuristic");

  static boolean isParameterizedTest(PsiClass psiClass) {
    for (JUnitParameterizedClassHeuristic heuristic : EP_NAME.getExtensions()) {
      if (heuristic.isParameterized(psiClass)) {
        return true;
      }
    }
    return false;
  }

  boolean isParameterized(PsiClass psiClass);
}
