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
package com.google.idea.blaze.clwb.oclang.run.test;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.clwb.CidrGoogleTestUtilAdapter;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.cidr.lang.psi.OCMacroCall;
import com.jetbrains.cidr.lang.psi.OCStruct;
import com.jetbrains.cidr.lang.symbols.OCSymbolHolderVirtualPsiElement;
import java.util.List;
import javax.annotation.Nullable;

/** Locate cpp test classes / methods for test UI navigation. */
public class BlazeCppTestLocator implements SMTestLocator {

  public static final BlazeCppTestLocator INSTANCE = new BlazeCppTestLocator();

  private BlazeCppTestLocator() {}

  @Override
  public List<Location> getLocation(
      String protocol, String path, Project project, GlobalSearchScope scope) {
    BlazeCppTestInfo testInfo = BlazeCppTestInfo.fromPath(protocol, path);
    if (testInfo == null) {
      return ImmutableList.of();
    }

    PsiElement psi = findPsiElement(project, testInfo);
    if (psi == null) {
      return ImmutableList.of();
    }
    GoogleTestSpecification gtest =
        new GoogleTestSpecification.FromGtestOutput(
            testInfo.suiteComponent(), testInfo.methodComponent());
    return ImmutableList.of(new GoogleTestLocation(psi, gtest));
  }

  @Nullable
  private static PsiElement findPsiElement(Project project, BlazeCppTestInfo testInfo) {
    if (testInfo.suite == null) {
      return null;
    }
    PsiElement testElement = null;
    if (testInfo.method != null) {
      testElement =
          CidrGoogleTestUtilAdapter.findGoogleTestSymbol(project, testInfo.suite, testInfo.method);
    } else if (testInfo.instantiation != null) {
      testElement =
          CidrGoogleTestUtilAdapter.findGoogleTestInstantiationSymbol(
              project, testInfo.suite, testInfo.instantiation, testInfo.suiteOrder);
    } else {
      testElement =
          CidrGoogleTestUtilAdapter.findAnyGoogleTestSymbolForSuite(project, testInfo.suite);
    }
    if (testElement == null) {
      return null;
    }
    while (!(testElement instanceof OCSymbolHolderVirtualPsiElement
            || testElement instanceof OCStruct
            || testElement instanceof OCMacroCall)
        && testElement != null) {
      PsiElement prev = testElement.getPrevSibling();
      testElement = prev == null ? testElement.getParent() : prev;
    }
    return testElement;
  }
}
