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
package com.google.idea.blaze.kotlin.run.producers;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.run.producers.RunConfigurationContext;
import com.google.idea.blaze.base.run.producers.TestContext;
import com.google.idea.blaze.base.run.producers.TestContextProvider;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

class KotlinTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    KtNamedFunction testMethod = PsiUtils.getParentOfType(element, KtNamedFunction.class, false);
    KtClass testClass = PsiUtils.getParentOfType(element, KtClass.class, false);
    if (testClass == null) {
      return null;
    }
    // TODO: detect test size from kotlin source code
    ListenableFuture<TargetInfo> target =
        TestTargetHeuristic.targetFutureForPsiElement(testClass, /* testSize= */ null);
    if (target == null) {
      return null;
    }
    String filter = getTestFilter(testClass, testMethod);
    String description = testClass.getName();
    if (testMethod != null) {
      description += "." + testMethod.getName();
    }
    PsiElement contextElement = testMethod != null ? testMethod : testClass;
    return TestContext.builder(contextElement, ExecutorType.DEBUG_SUPPORTED_TYPES)
        .setTarget(target)
        .setTestFilter(filter)
        .setDescription(description)
        .build();
  }

  @Nullable
  private static String getTestFilter(KtClass testClass, KtNamedFunction testMethod) {
    FqName fqName = testMethod != null ? testMethod.getFqName() : testClass.getFqName();
    return fqName != null ? fqName.toString() : null;
  }
}
