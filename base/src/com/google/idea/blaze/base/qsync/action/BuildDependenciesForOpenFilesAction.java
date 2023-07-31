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
package com.google.idea.blaze.base.qsync.action;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.qsync.TargetsToBuild;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.PopupPosititioner;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/** Action to build dependencies and enable analysis for all open editor tabs. */
public class BuildDependenciesForOpenFilesAction extends BlazeProjectAction {

  private final Logger logger = Logger.getInstance(BuildDependenciesForOpenFilesAction.class);

  @Override
  @NotNull
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent event) {
    BuildDependenciesHelper helper = new BuildDependenciesHelper(project);
    if (!helper.canEnableAnalysisNow()) {
      return;
    }
    // Each open source file may map to multiple targets, either because they're a build file
    // or because a source file is included in multiple targets.

    // Find the targets to build per source file, and de-dupe then such that if several source files
    // are built by the same set of targets, we consider them as one. Map these results back to an
    // original source file to so we can show it in the UI:
    ImmutableMap<TargetsToBuild, VirtualFile> targetsToBuild =
        Arrays.stream(FileEditorManager.getInstance(project).getAllEditors())
            .map(FileEditor::getFile)
            .collect(
                ImmutableMap.toImmutableMap(
                    helper::getTargetsToEnableAnalysisFor,
                    Function.identity(),
                    (a, b) -> a // we only care about 1 source file per target set
                    ));
    ImmutableListMultimap<Boolean, TargetsToBuild> targetsByAmbiguity =
        Multimaps.index(targetsToBuild.keySet(), TargetsToBuild::isAmbiguous);
    // we build all unambiguous targets:
    ImmutableSet<Label> unambiguousTargets =
        targetsByAmbiguity.get(false).stream()
            .map(TargetsToBuild::targets)
            .flatMap(Set::stream)
            .collect(ImmutableSet.toImmutableSet());
    ImmutableSet.Builder<TargetsToBuild> ambiguousTargetsBuilder = ImmutableSet.builder();
    for (TargetsToBuild ambiguous : targetsByAmbiguity.get(true)) {
      if (!Collections.disjoint(ambiguous.targets(), unambiguousTargets)) {
        // we already have (at least) one of these targets from the unambiguous set, so don't need
        // to choose one.
      } else {
        ambiguousTargetsBuilder.add(ambiguous);
      }
    }
    ImmutableSet<TargetsToBuild> ambiguousTargets = ambiguousTargetsBuilder.build();
    if (ambiguousTargets.isEmpty()) {
      helper.enableAnalysis(unambiguousTargets);
    }
    if (ambiguousTargets.size() == 1) {
      TargetsToBuild ambiguousOne = Iterables.getOnlyElement(ambiguousTargets);
      helper.chooseTargetToBuildFor(
          targetsToBuild.get(ambiguousOne),
          ambiguousOne,
          PopupPosititioner.showAtMousePointerOrCentered(event),
          chosen ->
              helper.enableAnalysis(
                  ImmutableSet.<Label>builder().addAll(unambiguousTargets).add(chosen).build()));
    } else {
      logger.warn(
          "Multiple ambiguous target sets for open files; not building them: "
              + ambiguousTargets.stream()
                  .map(targetsToBuild::get)
                  .map(VirtualFile::getPath)
                  .collect(Collectors.joining(", ")));
      if (!unambiguousTargets.isEmpty()) {
        helper.enableAnalysis(unambiguousTargets);
      } else {
        // TODO(mathewi) show an error?
        // or should we show multiple popups in parallel? (doesn't seem great if there are lots)
      }
    }
  }
}
