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

import com.google.idea.blaze.base.command.BlazeFlags;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.cidr.execution.testing.CidrTestUtil;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCFunctionDefinition;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.psi.OCMacroCallArgument;
import com.jetbrains.cidr.lang.psi.OCStruct;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCFunctionSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCStructSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCSymbolWithQualifiedName;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** A {@link PsiLocation} with corresponding gtest specification */
public class GoogleTestLocation extends PsiLocation<PsiElement> {

  public final GoogleTestSpecification gtest;
  @Nullable public final String testFilter;

  GoogleTestLocation(PsiElement psi, GoogleTestSpecification gtest) {
    super(psi);
    this.gtest = gtest;
    this.testFilter = gtest.testFilter();
  }

  /** The raw test filter string with '--test_filter=' prepended, or null if there is no filter. */
  @Nullable
  public String getTestFilterFlag() {
    return testFilter != null ? BlazeFlags.TEST_FILTER + "=" + testFilter : null;
  }

  @Nullable
  public static GoogleTestLocation findGoogleTest(Location<?> location) {
    if (location instanceof GoogleTestLocation) {
      return (GoogleTestLocation) location;
    }
    return findGoogleTest(location.getPsiElement());
  }

  @Nullable
  public static GoogleTestLocation findGoogleTest(PsiElement element) {
    // Copied from on CidrGoogleTestRunConfigurationProducer::findTestObject.
    // Precedence order (decreasing): class/function, macro, file
    PsiElement parent =
        PsiTreeUtil.getNonStrictParentOfType(element, OCFunctionDefinition.class, OCStruct.class);

    OCStructSymbol parentSymbol;
    if (parent instanceof OCStruct
        && ((parentSymbol = ((OCStruct) parent).getSymbol()) != null)
        && CidrTestUtil.isGoogleTestClass(parentSymbol)) {
      Couple<String> name = CidrTestUtil.extractGoogleTestName(parentSymbol);
      if (name != null) {
        return createFromClassAndMethod(parent, name.first, name.second);
      }
      String className = parentSymbol.getQualifiedName().getName();
      return createFromClass(parent, className);
    } else if (parent instanceof OCFunctionDefinition) {
      OCFunctionSymbol symbol = ((OCFunctionDefinition) parent).getSymbol();
      if (symbol != null) {
        OCSymbolWithQualifiedName<?> resolvedOwner = symbol.getResolvedOwner();
        if (resolvedOwner != null) {
          OCSymbol<?> owner = resolvedOwner.getDefinitionSymbol();
          if (owner instanceof OCStructSymbol
              && CidrTestUtil.isGoogleTestClass((OCStructSymbol) owner)) {
            OCStruct struct = (OCStruct) owner.locateDefinition();
            Couple<String> name = CidrTestUtil.extractGoogleTestName((OCStructSymbol) owner);
            if (name != null) {
              return createFromClassAndMethod(struct, name.first, name.second);
            }
            return createFromClass(struct, ((OCStructSymbol) owner).getQualifiedName().getName());
          }
        }
      }
    }

    // if we're still here, let's test for a macro and, as a last resort, a file.
    parent = PsiTreeUtil.getNonStrictParentOfType(element, OCMacroCall.class, OCFile.class);
    if (parent instanceof OCMacroCall) {
      OCMacroCall gtestMacro = CidrTestUtil.findGoogleTestMacros(parent);
      if (gtestMacro != null) {
        List<OCMacroCallArgument> arguments = gtestMacro.getArguments();
        if (arguments.size() >= 2) {
          OCMacroCallArgument suiteArg = arguments.get(0);
          OCMacroCallArgument testArg = arguments.get(1);

          // if the element is the first argument of macro call,
          // then running entire suite, otherwise only a current test
          boolean isSuite =
              isFirstArgument(PsiTreeUtil.getParentOfType(element, OCMacroCallArgument.class))
                  || isFirstArgument(element.getPrevSibling());
          String suiteName = CidrTestUtil.extractArgumentValue(suiteArg);
          String testName = CidrTestUtil.extractArgumentValue(testArg);
          OCStructSymbol symbol =
              CidrTestUtil.findGoogleTestSymbol(element.getProject(), suiteName, testName);
          if (symbol != null) {
            OCStruct targetElement = (OCStruct) symbol.locateDefinition();
            return createFromClassAndMethod(targetElement, suiteName, isSuite ? null : testName);
          }
        }
      }
      Couple<String> suite = CidrTestUtil.extractFullSuiteNameFromMacro(parent);
      if (suite != null) {
        Collection<OCStructSymbol> res =
            CidrTestUtil.findGoogleTestSymbolsForSuiteRandomly(
                element.getProject(), suite.first, true);
        if (res.size() != 0) {
          OCStruct struct = (OCStruct) res.iterator().next().locateDefinition();
          GoogleTestSpecification gtest =
              new GoogleTestSpecification.FromPsiElement(suite.first, null, suite.second, null);
          return new GoogleTestLocation(struct, gtest);
        }
      }
    } else if (parent instanceof OCFile) {
      return createFromFile(parent);
    }
    return null;
  }

  private static boolean isFirstArgument(@Nullable PsiElement element) {
    OCMacroCall macroCall = PsiTreeUtil.getParentOfType(element, OCMacroCall.class);
    if (macroCall != null) {
      List<OCMacroCallArgument> arguments = macroCall.getArguments();
      return arguments.size() > 0 && arguments.get(0).equals(element);
    }
    return false;
  }

  @Nullable
  private static GoogleTestLocation createFromFile(@Nullable PsiElement element) {
    return createFromClassAndMethod(element, null, null);
  }

  @Nullable
  private static GoogleTestLocation createFromClass(
      @Nullable PsiElement element, @Nullable String className) {
    return createFromClassAndMethod(element, className, null);
  }

  @Nullable
  private static GoogleTestLocation createFromClassAndMethod(
      @Nullable PsiElement element, @Nullable String classOrSuiteName, @Nullable String testName) {
    if (element == null) {
      return null;
    }
    GoogleTestSpecification gtest =
        new GoogleTestSpecification.FromPsiElement(classOrSuiteName, testName, null, null);
    return new GoogleTestLocation(element, gtest);
  }
}
