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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags.JUnitVersion;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Matches junit test sources to test targets with junit* in their name.
 */
public class JUnitTestHeuristic implements TestTargetHeuristic {

  /**
   * Determine whether a class has a particular JUnit Version.
   */
  public static Optional<JUnitVersion> jUnitVersion(PsiClass psiClass) {
    if (JUnitUtil.isJUnit5TestClass(psiClass, true) || hasJUnit5Method(psiClass)) {
      return Optional.of(JUnitVersion.JUNIT_5);
    } else if (JUnitUtil.isJUnit4TestClass(psiClass)) {
      return Optional.of(JUnitVersion.JUNIT_4);
    } else if (JUnitUtil.isJUnit3TestClass(psiClass)) {
      return Optional.of(JUnitVersion.JUNIT_3);
    } else {
      return Optional.empty();
    }
  }

  /**
   * Determine whether a set of classes has a particular JUnit Version.
   * If there are several JUnit versions, it will pick the first one.
   */
  public static Optional<JUnitVersion> jUnitVersion(Collection<PsiClass> classes) {
    for (PsiClass psiClass : classes) {
      Optional<JUnitVersion> version = JUnitTestHeuristic.jUnitVersion(psiClass);
      if (version.isPresent()) {
        return version;
      }
    }
    return Optional.empty();
  }

  private static boolean hasJUnit5Method(PsiClass psiClass) {
    PsiMethod[] methods = psiClass.getAllMethods();
    return Arrays.stream(methods)
        .anyMatch((method) -> MetaAnnotationUtil.isMetaAnnotated(method, JUnitUtil.TEST5_JUPITER_ANNOTATIONS));
  }

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    Optional<JUnitVersion> sourceVersion = junitVersion(sourcePsiFile);
    if (sourceVersion == null || sourceVersion.isEmpty()) {
      return false;
    }
    String targetName = target.label.targetName().toString().toLowerCase();
    switch (sourceVersion.get()) {
      case JUNIT_5:
        return targetName.contains("junit5");
      case JUNIT_4:
        return targetName.contains("junit4");
      case JUNIT_3:
        return targetName.contains("junit3");
    }
    return false;
  }

  @Nullable
  private Optional<JUnitVersion> junitVersion(@Nullable PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return null;
    }
    return ReadAction.compute(() -> junitVersion((PsiClassOwner) psiFile));
  }

  @Nullable
  private Optional<JUnitVersion> junitVersion(PsiClassOwner classOwner) {
    for (PsiClass psiClass : classOwner.getClasses()) {
      Optional<JUnitVersion> version = jUnitVersion(psiClass);
      if (version.isPresent()) {
        return version;
      }
    }
    return Optional.empty();
  }
}
