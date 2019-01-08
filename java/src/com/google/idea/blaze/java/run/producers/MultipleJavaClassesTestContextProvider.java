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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Runs tests in all selected java classes (or all classes below selected directory). Ignores
 * classes spread across multiple test targets.
 */
class MultipleJavaClassesTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    boolean outsideProject = context.getModule() == null;
    if (outsideProject) {
      // TODO(brendandouglas): resolve PSI asynchronously for files outside the project
      return null;
    }
    PsiElement location = context.getPsiLocation();
    if (location instanceof PsiDirectory) {
      PsiDirectory dir = (PsiDirectory) location;
      TargetInfo target = getTestTargetIfUnique(dir);
      return target != null ? fromDirectory(target, dir) : null;
    }
    Set<PsiClass> testClasses = selectedTestClasses(context);
    if (testClasses.size() < 2) {
      return null;
    }
    TargetInfo target = getTestTargetIfUnique(testClasses);
    if (target == null) {
      return null;
    }
    testClasses = ProducerUtils.includeInnerTestClasses(testClasses);
    return fromClasses(target, testClasses);
  }

  @Nullable
  private static TestContext fromDirectory(TargetInfo target, PsiDirectory dir) {
    String packagePrefix =
        ProjectFileIndex.SERVICE
            .getInstance(dir.getProject())
            .getPackageNameByDirectory(dir.getVirtualFile());
    if (packagePrefix == null) {
      return null;
    }
    String description =
        packagePrefix.isEmpty() ? null : String.format("all in directory '%s'", dir.getName());
    String testFilter = packagePrefix.isEmpty() ? null : packagePrefix;
    return TestContext.builder()
        .setTarget(target)
        .setSourceElement(dir)
        .setTestFilter(testFilter)
        .setDescription(description)
        .build();
  }

  @Nullable
  private static TestContext fromClasses(TargetInfo target, Set<PsiClass> classes) {
    Map<PsiClass, Collection<Location<?>>> methodsPerClass =
        classes.stream().collect(Collectors.toMap(c -> c, c -> ImmutableList.of()));
    String filter = BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(methodsPerClass);
    if (filter == null || filter.isEmpty()) {
      return null;
    }

    PsiClass sampleClass =
        classes.stream()
            .min(
                Comparator.comparing(
                    PsiClass::getName, Comparator.nullsLast(Comparator.naturalOrder())))
            .orElse(null);
    if (sampleClass == null) {
      return null;
    }
    String name = sampleClass.getName();
    if (name != null && classes.size() > 1) {
      name += String.format(" and %s others", classes.size() - 1);
    }
    return TestContext.builder()
        .setTarget(target)
        .setSourceElement(sampleClass)
        .setTestFilter(filter)
        .setDescription(name)
        .build();
  }

  private static Set<PsiClass> selectedTestClasses(ConfigurationContext context) {
    DataContext dataContext = context.getDataContext();
    PsiElement[] elements = getSelectedPsiElements(dataContext);
    if (elements == null) {
      return ImmutableSet.of();
    }
    return Arrays.stream(elements)
        .map(ProducerUtils::getTestClass)
        .filter(Objects::nonNull)
        .filter(testClass -> !testClass.hasModifierProperty(PsiModifier.ABSTRACT))
        .collect(Collectors.toSet());
  }

  @Nullable
  private static PsiElement[] getSelectedPsiElements(DataContext context) {
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
    if (elements != null) {
      return elements;
    }
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(context);
    return element != null ? new PsiElement[] {element} : null;
  }

  @Nullable
  private static TargetInfo getTestTargetIfUnique(PsiDirectory directory) {
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(directory.getProject());
    if (BlazePackage.hasBlazePackageChild(directory, dir -> relevantDirectory(index, dir))) {
      return null;
    }
    Set<PsiClass> classes = new HashSet<>();
    addClassesInDirectory(directory, classes);
    return getTestTargetIfUnique(classes);
  }

  private static boolean relevantDirectory(ProjectFileIndex index, PsiDirectory dir) {
    // only search under java source roots
    return index.isInSourceContent(dir.getVirtualFile());
  }

  private static void addClassesInDirectory(PsiDirectory directory, Set<PsiClass> list) {
    Collections.addAll(list, JavaDirectoryService.getInstance().getClasses(directory));
    for (PsiDirectory child : directory.getSubdirectories()) {
      addClassesInDirectory(child, list);
    }
  }

  @Nullable
  private static TargetInfo getTestTargetIfUnique(Set<PsiClass> classes) {
    TargetInfo testTarget = null;
    for (PsiClass psiClass : classes) {
      TargetInfo target = testTargetForClass(psiClass);
      if (target == null) {
        continue;
      }
      if (testTarget != null && !testTarget.equals(target)) {
        return null;
      }
      testTarget = target;
    }
    return testTarget;
  }

  @Nullable
  private static TargetInfo testTargetForClass(PsiClass psiClass) {
    PsiClass testClass = ProducerUtils.getTestClass(psiClass);
    if (testClass == null || testClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }
    TestSize testSize = TestSizeFinder.getTestSize(psiClass);
    return TestTargetHeuristic.testTargetForPsiElement(psiClass, testSize);
  }
}
