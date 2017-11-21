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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.CommonProcessors;
import com.jetbrains.cidr.execution.testing.google.CidrGoogleTestUtil;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.psi.OCStruct;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCStructSymbol;
import com.jetbrains.cidr.lang.symbols.symtable.OCGlobalProjectSymbolsCache;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Locate cpp test classes / methods for test UI navigation. */
public class BlazeCppTestLocator implements SMTestLocator {

  public static final BlazeCppTestLocator INSTANCE = new BlazeCppTestLocator();

  private static final Pattern SUITE_PATTERN = Pattern.compile("((\\w+)/)?(\\w+)(/(\\d+))?");
  private static final Pattern METHOD_PATTERN = Pattern.compile("(\\w+)(/(\\d+))?");

  private BlazeCppTestLocator() {}

  @Override
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    GoogleTestLocation location = null;
    if (SmRunnerUtils.GENERIC_SUITE_PROTOCOL.equals(protocol)) {
      location = getLocation(project, path, null);
    } else if (SmRunnerUtils.GENERIC_TEST_PROTOCOL.equals(protocol)) {
      String[] components = path.split(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER);
      location = components.length != 2 ? null : getLocation(project, components[0], components[1]);
    }
    return location != null ? ImmutableList.of(location) : ImmutableList.of();
  }

  @Nullable
  private static GoogleTestLocation getLocation(
      Project project, String suiteComponent, @Nullable String methodComponent) {
    Matcher matcher = SUITE_PATTERN.matcher(suiteComponent);
    if (!matcher.matches()) {
      return null;
    }
    String instantiation = matcher.group(2);
    String suite = matcher.group(3);
    String method = null;
    if (methodComponent != null) {
      matcher = METHOD_PATTERN.matcher(methodComponent);
      if (!matcher.matches()) {
        return null;
      }
      method = matcher.group(1);
    }
    PsiElement psi = findPsiElement(project, instantiation, suite, method);
    if (psi == null) {
      return null;
    }
    GoogleTestSpecification gtest =
        new GoogleTestSpecification.FromGtestOutput(suiteComponent, methodComponent);
    return new GoogleTestLocation(psi, gtest);
  }

  @Nullable
  private static PsiElement findPsiElement(
      Project project,
      @Nullable String instantiation,
      @Nullable String suite,
      @Nullable String method) {
    if (suite == null) {
      return null;
    }
    OCSymbol<?> symbol;
    if (method != null) {
      symbol = CidrGoogleTestUtil.findGoogleTestSymbol(project, suite, method);
    } else if (instantiation != null) {
      symbol = CidrGoogleTestUtil.findGoogleTestInstantiationSymbol(project, suite, instantiation);
    } else {
      symbol = findSuiteSymbol(project, suite);
    }
    if (symbol == null) {
      return null;
    }
    PsiElement psi = symbol.locateDefinition();
    while (!(psi instanceof OCStruct || psi instanceof OCMacroCall) && psi != null) {
      PsiElement prev = psi.getPrevSibling();
      psi = prev == null ? psi.getParent() : prev;
    }
    return psi;
  }

  @Nullable
  private static OCStructSymbol findSuiteSymbol(Project project, String suite) {
    CommonProcessors.FindProcessor<OCSymbol> processor =
        new CommonProcessors.FindProcessor<OCSymbol>() {
          @Override
          protected boolean accept(OCSymbol symbol) {
            return symbol instanceof OCStructSymbol
                && CidrGoogleTestUtil.isGoogleTestClass((OCStructSymbol) symbol);
          }
        };
    OCGlobalProjectSymbolsCache.processTopLevelAndMemberSymbols(project, processor, suite);
    if (processor.isFound()) {
      return (OCStructSymbol) processor.getFoundValue();
    }
    Collection<OCStructSymbol> symbolsForSuite =
        CidrGoogleTestUtil.findGoogleTestSymbolsForSuiteSorted(project, suite);
    return Iterables.getFirst(symbolsForSuite, null);
  }
}
