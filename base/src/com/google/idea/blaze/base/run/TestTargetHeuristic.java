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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo.TestSize;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

/** Heuristic to match test targets to source files. */
public interface TestTargetHeuristic {

  ExtensionPointName<TestTargetHeuristic> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TestTargetHeuristic");

  /** Finds a test rule associated with a given {@link PsiElement}. */
  @Nullable
  static Label testTargetForPsiElement(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) {
      return null;
    }
    VirtualFile vf = psiFile.getVirtualFile();
    File file = vf != null ? new File(vf.getPath()) : null;
    if (file == null) {
      return null;
    }
    Collection<TargetIdeInfo> rules =
        TestTargetFinder.getInstance(element.getProject()).testTargetsForSourceFile(file);
    return chooseTestTargetForSourceFile(element.getProject(), psiFile, file, rules, null);
  }

  /**
   * Given a source file and all test rules reachable from that file, chooses a test rule based on
   * available filters, falling back to choosing the first one if there is no match.
   */
  @Nullable
  static Label chooseTestTargetForSourceFile(
      Project project,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      Collection<TargetIdeInfo> targets,
      @Nullable TestSize testSize) {

    for (TestTargetHeuristic filter : EP_NAME.getExtensions()) {
      TargetIdeInfo match =
          targets
              .stream()
              .filter(
                  target ->
                      filter.matchesSource(project, target, sourcePsiFile, sourceFile, testSize))
              .findFirst()
              .orElse(null);

      if (match != null) {
        return match.key.label;
      }
    }
    return targets.isEmpty() ? null : targets.iterator().next().key.label;
  }

  /** Returns true if the rule and source file match, according to this heuristic. */
  boolean matchesSource(
      Project project,
      TargetIdeInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize);
}
