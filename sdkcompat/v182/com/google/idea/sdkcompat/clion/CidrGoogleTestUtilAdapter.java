/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.clion;

import com.google.common.collect.Iterables;
import com.google.idea.sdkcompat.cidr.OCSymbolAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestUtil;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCStructSymbol;
import java.util.Collection;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. #api182 */
public class CidrGoogleTestUtilAdapter {
  @Nullable
  public static PsiElement findGoogleTestSymbol(Project project) {
    Collection<OCStructSymbol> results =
        CidrGoogleTestUtil.findGoogleTestSymbolsForSuiteRandomly(project, null, true);
    return resolveToPsiElement(Iterables.getFirst(results, null));
  }

  @Nullable
  public static PsiElement findGoogleTestSymbol(
      Project project, String suiteName, String testName) {
    return resolveToPsiElement(
        CidrGoogleTestUtil.findGoogleTestSymbol(project, suiteName, testName));
  }

  @Nullable
  public static PsiElement findGoogleTestInstantiationSymbol(
      Project project, String suite, String instantiation) {
    return resolveToPsiElement(
        CidrGoogleTestUtil.findGoogleTestInstantiationSymbol(project, suite, instantiation));
  }

  public static boolean isGoogleTestClass(OCStructSymbol symbol, Project project) {
    return CidrGoogleTestUtil.isGoogleTestClass(symbol, project);
  }

  @Nullable
  public static PsiElement findAnyGoogleTestSymbolForSuite(Project project, String suite) {
    Collection<OCStructSymbol> results =
        CidrGoogleTestUtil.findGoogleTestSymbolsForSuiteRandomly(project, suite, true);
    return resolveToPsiElement(Iterables.getFirst(results, null));
  }

  @Nullable
  public static OCMacroCall findGoogleTestMacros(PsiElement element) {
    return CidrGoogleTestUtil.findGoogleTestMacros(element);
  }

  @Nullable
  public static Couple<String> extractFullSuiteNameFromMacro(PsiElement element) {
    return CidrGoogleTestUtil.extractFullSuiteNameFromMacro(element);
  }

  public static boolean fileIncludesGoogleTest(OCFile file) {
    return CidrGoogleTestUtil.fileIncludesGoogleTest(file);
  }

  @Nullable
  private static PsiElement resolveToPsiElement(@Nullable OCSymbol symbol) {
    if (symbol == null) {
      return null;
    }
    return OCSymbolAdapter.locateDefinition(symbol, symbol.getProject());
  }
}
