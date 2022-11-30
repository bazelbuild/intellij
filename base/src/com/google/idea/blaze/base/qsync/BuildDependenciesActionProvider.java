/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.JBUI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the actions to be used with the inspection widget. The inspection widget is the
 * tri-color icon at the top-right of files showing analysis results. This class provides the action
 * that sits there and builds the file dependencies and enables analysis.
 */
public class BuildDependenciesActionProvider implements InspectionWidgetActionProvider {

  @Nullable
  @Override
  public AnAction createAction(@NotNull Editor editor) {
    if (!QuerySyncManager.isEnabled()) {
      return null;
    }
    if (!editor.getEditorKind().equals(EditorKind.MAIN_EDITOR)) {
      return null;
    }
    return new BuildDependencies(editor);
  }

  private static class BuildDependencies extends AnAction implements CustomComponentAction {

    private final Editor editor;

    public BuildDependencies(@NotNull Editor editor) {
      super("Build file dependencies");
      this.editor = editor;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = editor.getProject();
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, true);
      DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
      BlazeImportSettings settings =
          BlazeImportSettingsManager.getInstance(project).getImportSettings();

      if (!BlazeSyncStatus.getInstance(project).syncInProgress()) {
        String rel =
            Paths.get(settings.getWorkspaceRoot())
                .relativize(Paths.get(psiFile.getVirtualFile().getPath()))
                .toString();
        WorkspacePath wp = WorkspacePath.createIfValid(rel);
        List<WorkspacePath> wps = new ArrayList<>();
        wps.add(wp);
        QuerySyncManager.getInstance(project).build(wps);
      }
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!BlazeSyncStatus.getInstance(project).syncInProgress());
    }

    @Override
    @NotNull
    public JComponent createCustomComponent(
        @NotNull Presentation presentation, @NotNull String place) {
      return new ActionButtonWithText(this, presentation, place, JBUI.size(18));
    }
  }
}
