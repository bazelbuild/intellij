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
package com.google.idea.blaze.clwb.run.producers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.google.idea.blaze.clwb.run.test.GoogleTestLocation;
import com.google.idea.blaze.clwb.run.test.GoogleTestSpecification;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;

/** Provides run configurations related to C/C++ test classes in Blaze. */
class CppTestContextProvider implements TestContextProvider {
  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement element = selectedPsiElement(context);
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }
    GoogleTestLocation test = GoogleTestLocation.findGoogleTest(element, context.getProject());
    if (test == null) {
      return null;
    }
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(test.getPsiElement(), /* testSize= */ null);
    if (target == null) {
      return null;
    }
    GoogleTestSpecification gtest = test.gtest;
    String description =
        gtest.description() != null
            ? String.format("%s (%s)", gtest.description(), file.getName())
            : file.getName();

    return TestContext.builder(test.getPsiElement(), ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(gtest.testFilter())
        .setDescription(description)
        .build();
  }

  /** The single selected {@link PsiElement}. Returns null if multiple elements are selected. */
  @Nullable
  private static PsiElement selectedPsiElement(ConfigurationContext context) {
    PsiElement[] psi = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext());
    if (psi != null && psi.length > 1) {
      return null; // multiple elements selected.
    }
    Location<?> location = context.getLocation();
    return location != null ? location.getPsiElement() : null;
  }
}
