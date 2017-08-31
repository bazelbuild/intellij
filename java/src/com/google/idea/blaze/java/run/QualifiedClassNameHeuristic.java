/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo.TestSize;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/** Matches test targets to source files based on fully qualified class names. */
public class QualifiedClassNameHeuristic implements TestTargetHeuristic {

  @Override
  public boolean matchesSource(
      Project project,
      TargetIdeInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    if (!(sourcePsiFile instanceof PsiClassOwner)) {
      return false;
    }
    String targetName = target.key.label.targetName().toString();
    if (!targetName.contains(".")) {
      return false;
    }
    for (PsiClass psiClass : ((PsiClassOwner) sourcePsiFile).getClasses()) {
      String fqcn = psiClass.getQualifiedName();
      if (fqcn != null && fqcn.endsWith(targetName)) {
        return true;
      }
    }
    return false;
  }
}
