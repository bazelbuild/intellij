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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.intellij.util.CommonProcessors.FindProcessor;
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestFramework;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestUtilObsolete;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.symbols.OCResolveContext;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCStructSymbol;
import com.jetbrains.cidr.lang.symbols.symtable.OCGlobalProjectSymbolsCache;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. #api182 */
public class CidrGoogleTestUtilAdapter {
  @Nullable
  public static PsiElement findGoogleTestSymbol(Project project) {
    return findGoogleTestSymbol(project, testScopeElement -> true);
  }

  @Nullable
  public static PsiElement findGoogleTestSymbol(
      Project project, String suiteName, String testName) {
    CidrGoogleTestFramework instance = CidrGoogleTestFramework.getInstance();
    final String prefixedSuiteName = "suite:" + suiteName;
    final String prefixedTestName = "test:" + testName;

    FindFirstWithPredicateProcessor<CidrTestScopeElement> processor =
        new FindFirstWithPredicateProcessor<CidrTestScopeElement>(
            (CidrTestScopeElement testElement) ->
                prefixedSuiteName.equals(testElement.getSuiteName())
                    && prefixedTestName.equals(testElement.getTestName()));
    instance.consumeTestObjects(project, GlobalSearchScope.allScope(project), processor);
    if (!processor.isFound()) {
      return null;
    }
    CidrTestScopeElement testElement = processor.getFoundValue();
    if (testElement == null) {
      return null;
    }
    return testElement.getElement();
  }

  @Nullable
  public static PsiElement findGoogleTestInstantiationSymbol(
      Project project, String testCaseName, String prefix) {
    CidrGoogleTestFramework instance = CidrGoogleTestFramework.getInstance();
    FindFirstWithPredicateProcessor<CidrTestScopeElement> processor =
        new FindFirstWithPredicateProcessor<CidrTestScopeElement>(
            (CidrTestScopeElement testElement) -> {
              PsiElement element = testElement.getElement();
              String name = ((OCSymbol) element).getName();
              String paramTestName = "gtest_" + prefix + testCaseName; // gtest-param-test.h
              String typedTestName = "gtest_" + prefix + "_" + testCaseName; // gtest-typed-test.h
              return name.equals(paramTestName) || name.equals(typedTestName);
            });
    instance.consumeTestObjects(project, GlobalSearchScope.allScope(project), processor);
    if (!processor.isFound()) {
      return null;
    }
    CidrTestScopeElement testElement = processor.getFoundValue();
    if (testElement == null) {
      return null;
    }
    return testElement.getElement();
  }

  /* TODO: Convert to not use CidrGoogleTestUtilObsolete. */
  public static boolean isGoogleTestClass(OCStructSymbol symbol, Project project) {
    return CidrGoogleTestUtilObsolete.isGoogleTestClass(symbol, project);
  }

  @Nullable
  public static PsiElement findAnyGoogleTestSymbolForSuite(Project project, String suite) {
    Collection<PsiElement> symbolsForSuite =
        CidrGoogleTestUtilAdapter.findGoogleTestSymbolsForSuite(project, suite);
    return Iterables.getFirst(symbolsForSuite, null);
  }

  private static Collection<PsiElement> findGoogleTestSymbolsForSuite(
      Project project, String suite) {
    final String prefixedSuiteName = "suite:" + suite;
    Collection<CidrTestScopeElement> result =
        findGoogleTestSymbols(
            project, testScopeElement -> prefixedSuiteName.equals(testScopeElement.getSuiteName()));
    return result.stream().map(CidrTestScopeElement::getElement).collect(Collectors.toList());
  }

  /* TODO: Convert to not use CidrGoogleTestUtilObsolete. */
  @Nullable
  public static OCMacroCall findGoogleTestMacros(@Nullable PsiElement element) {
    return CidrGoogleTestUtilObsolete.findGoogleTestMacros(element);
  }

  /* TODO: Convert to not use CidrGoogleTestUtilObsolete. */
  @Nullable
  public static Couple<String> extractFullSuiteNameFromMacro(PsiElement element) {
    return CidrGoogleTestUtilObsolete.extractFullSuiteNameFromMacro(element);
  }

  public static boolean fileIncludesGoogleTest(PsiFile file) {
    CollectProcessor<OCSymbol> processor =
        new CollectProcessor<OCSymbol>() {
          @Override
          protected boolean accept(OCSymbol symbol) {
            OCResolveContext context = OCResolveContext.forPsi(file);
            return symbol.getType().getCanonicalName(context).equals("::testing::TestCase");
          }
        };
    Project project = file.getProject();
    OCGlobalProjectSymbolsCache.processTopLevelAndMemberSymbols(project, processor, "TestCase");
    return processor.getResults().stream()
        .filter(symbol -> symbol.getContainingFile() != null)
        .anyMatch(
            symbol ->
                OCImportGraph.getAllHeaderRoots(project, symbol.getContainingFile())
                    .contains(file.getVirtualFile()));
  }

  @Nullable
  private static PsiElement findGoogleTestSymbol(
      Project project, Predicate<CidrTestScopeElement> predicate) {
    CidrGoogleTestFramework instance = CidrGoogleTestFramework.getInstance();
    FindFirstWithPredicateProcessor<CidrTestScopeElement> processor =
        new FindFirstWithPredicateProcessor<CidrTestScopeElement>(
            cidrTestScopeElement -> {
              PsiElement element = cidrTestScopeElement.getElement();
              return predicate.test(cidrTestScopeElement);
            });
    instance.consumeTestObjects(project, GlobalSearchScope.allScope(project), processor);
    CidrTestScopeElement testScopeElement = processor.getFoundValue();
    if (testScopeElement == null) {
      return null;
    }
    return testScopeElement.getElement();
  }

  private static Collection<CidrTestScopeElement> findGoogleTestSymbols(
      Project project, Predicate<CidrTestScopeElement> predicate) {
    CidrGoogleTestFramework instance = CidrGoogleTestFramework.getInstance();
    CollectProcessor<CidrTestScopeElement> processor =
        new CollectProcessor<CidrTestScopeElement>() {
          @Override
          protected boolean accept(CidrTestScopeElement cidrTestScopeElement) {
            return predicate.test(cidrTestScopeElement);
          }
        };
    instance.consumeTestObjects(project, GlobalSearchScope.allScope(project), processor);
    return processor.getResults();
  }

  private static class FindFirstWithPredicateProcessor<T> extends FindProcessor<T> {
    private final Predicate<T> predicate;

    FindFirstWithPredicateProcessor(Predicate<T> predicate) {
      this.predicate = predicate;
    }

    @Override
    protected boolean accept(T t) {
      return !isFound() && predicate.test(t);
    }
  }
}
