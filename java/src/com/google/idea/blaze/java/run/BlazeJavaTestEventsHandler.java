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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import java.util.List;
import javax.annotation.Nullable;

/** Provides java-specific methods needed by the SM-runner test UI. */
public class BlazeJavaTestEventsHandler extends BlazeTestEventsHandler {

  public BlazeJavaTestEventsHandler() {
    super("Blaze Java Test");
  }

  @Override
  public SMTestLocator getTestLocator() {
    return JavaTestLocator.INSTANCE;
  }

  @Override
  public String suiteLocationUrl(String name) {
    return JavaTestLocator.SUITE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + name;
  }

  @Override
  public String testLocationUrl(String name, @Nullable String classname) {
    if (classname == null) {
      return null;
    }
    return JavaTestLocator.TEST_PROTOCOL + URLUtil.SCHEME_SEPARATOR + classname + "." + name;
  }

  @Override
  public String suiteDisplayName(String rawName) {
    String name = StringUtil.trimEnd(rawName, '.');
    int lastPointIx = name.lastIndexOf('.');
    return lastPointIx != -1 ? name.substring(lastPointIx + 1, name.length()) : name;
  }

  @Nullable
  @Override
  public String getTestFilter(Project project, List<AbstractTestProxy> failedTests) {
    GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);
    MultiMap<PsiClass, PsiMethod> failedMethodsPerClass = new MultiMap<>();
    for (AbstractTestProxy test : failedTests) {
      appendTest(failedMethodsPerClass, test.getLocation(project, projectScope));
    }
    String filter = BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(failedMethodsPerClass);
    return filter != null ? BlazeFlags.TEST_FILTER + "=" + filter : null;
  }

  private static void appendTest(
      MultiMap<PsiClass, PsiMethod> testMap, @Nullable Location<?> testLocation) {
    if (testLocation == null) {
      return;
    }
    PsiElement method = testLocation.getPsiElement();
    if (!(method instanceof PsiMethod)) {
      return;
    }
    PsiClass psiClass = ((PsiMethod) method).getContainingClass();
    if (psiClass != null) {
      testMap.putValue(psiClass, (PsiMethod) method);
    }
  }
}
