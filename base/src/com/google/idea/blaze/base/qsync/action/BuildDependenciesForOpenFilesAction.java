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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** Action to build dependencies and enable analysis for all open editor tabs. */
public class BuildDependenciesForOpenFilesAction extends BlazeProjectAction {

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
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BuildDependenciesHelper helper = new BuildDependenciesHelper(project);
    if (!helper.canEnableAnalysisNow()) {
      return;
    }
    ImmutableList<Path> paths =
        Arrays.stream(FileEditorManager.getInstance(project).getAllEditors())
            .map(FileEditor::getFile)
            .map(helper::getRelativePathToEnableAnalysisFor)
            .flatMap(Optional::stream)
            .collect(toImmutableList());
    if (!paths.isEmpty()) {
      helper.enableAnalysis(paths);
    }
  }
}
