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

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
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
    Collection<TargetInfo> rules =
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
      Collection<TargetInfo> targets,
      @Nullable TestSize testSize) {
    if (targets.isEmpty()) {
      return null;
    }
    List<TargetInfo> filteredTargets = new ArrayList<>(targets);
    for (TestTargetHeuristic filter : EP_NAME.getExtensions()) {
      List<TargetInfo> matches =
          filteredTargets
              .stream()
              .filter(
                  target ->
                      filter.matchesSource(project, target, sourcePsiFile, sourceFile, testSize))
              .collect(Collectors.toList());
      if (matches.size() == 1) {
        return matches.get(0).label;
      }
      if (!matches.isEmpty()) {
        // A higher-priority filter found more than one match -- subsequent filters will only
        // consider these matches.
        filteredTargets = matches;
      }
    }
    return filteredTargets.iterator().next().label;
  }

  /** Returns true if the rule and source file match, according to this heuristic. */
  boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize);
}
