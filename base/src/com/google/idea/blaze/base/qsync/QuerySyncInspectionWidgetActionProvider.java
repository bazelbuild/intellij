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

import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.JBUI;
import icons.BlazeIcons;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the actions to be used with the inspection widget. The inspection widget is the
 * tri-color icon at the top-right of files showing analysis results. This class provides the action
 * that sits there and builds the file dependencies and enables analysis.
 */
public class QuerySyncInspectionWidgetActionProvider implements InspectionWidgetActionProvider {

  @Nullable
  @Override
  public AnAction createAction(@NotNull Editor editor) {
    if (!QuerySync.isEnabled()) {
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
      QuerySyncManager.getInstance(project).enableAnalysis(psiFile);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(!BlazeSyncStatus.getInstance(e.getProject()).syncInProgress());
      super.update(e);
    }

    @Override
    @NotNull
    public JComponent createCustomComponent(
        @NotNull Presentation presentation, @NotNull String place) {
      presentation.setIcon(BlazeIcons.Logo);
      return new ActionButton(this, presentation, place, JBUI.size(16));
    }
  }
}
