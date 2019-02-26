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
package com.google.idea.blaze.golang.run.producers;

import com.goide.execution.testing.GoTestFinder;
import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionOrMethodDeclaration;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;

class GoTestContextProvider implements TestContextProvider {

  @Override
  public boolean webTestCompatible() {
    return true;
  }

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement element = context.getPsiLocation();
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (!(file instanceof GoFile) || !GoTestFinder.isTestFile(file)) {
      return null;
    }
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(element, /* testSize= */ null);
    if (target == null) {
      return null;
    }
    GoFunctionOrMethodDeclaration function = GoTestFinder.findTestFunctionInContext(element);
    if (function == null) {
      return TestContext.builder(/* sourceElement= */ file, ExecutorType.DEBUG_SUPPORTED_TYPES)
          .setTarget(target)
          .setDescription(file.getName())
          .build();
    }
    return TestContext.builder(/* sourceElement= */ function, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter("^" + function.getName() + "$")
        .setDescription(String.format("%s#%s", file.getName(), function.getName()))
        .build();
  }
}
