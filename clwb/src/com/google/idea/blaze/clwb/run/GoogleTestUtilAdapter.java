/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.run;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.cidr.execution.testing.CidrTestScopeElement;
import java.util.function.Predicate;
import javax.annotation.Nullable;

public interface GoogleTestUtilAdapter {
  ExtensionPointName<GoogleTestUtilAdapter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.clwb.googleTestUtilAdapter");

  @Nullable
  static PsiElement findGoogleTestSymbol(Project project) {
    if (EP_NAME.getExtensionList().size() > 1) {
      throw new IllegalStateException("More than 1 extension for " + EP_NAME.getName() + " is not supported");
    }

    GoogleTestUtilAdapter adapter = EP_NAME.getPoint().getExtensionList().stream().findFirst().orElse(null);
    if (adapter != null) {
      return adapter.findGoogleTestSymbol(project, testScopeElement -> true);
    }

    return null;
  }

  PsiElement findGoogleTestSymbol(
      Project project, Predicate<CidrTestScopeElement> predicate);
}
