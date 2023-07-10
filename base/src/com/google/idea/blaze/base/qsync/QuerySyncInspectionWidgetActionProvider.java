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

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.common.Label;
import com.intellij.icons.AllIcons.Actions;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.FontUIResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides the actions to be used with the inspection widget. The inspection widget is the
 * tri-color icon at the top-right of files showing analysis results. This class provides the action
 * that sits there and builds the file dependencies and enables analysis
 */
public class QuerySyncInspectionWidgetActionProvider implements InspectionWidgetActionProvider {

  @Nullable
  @Override
  public AnAction createAction(@NotNull Editor editor) {
    if (!QuerySync.isEnabled()) {
      return null;
    }
    if (!Blaze.isBlazeProject(editor.getProject())) {
      return null;
    }
    if (!editor.getEditorKind().equals(EditorKind.MAIN_EDITOR)) {
      return null;
    }
    return new BuildDependencies(editor);
  }

  private static class BuildDependencies extends AnAction
      implements CustomComponentAction, DumbAware {

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
      Project project = e.getProject();
      presentation.setText("");
      if (project == null) {
        presentation.setEnabled(false);
        return;
      }

      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      VirtualFile vf = psiFile != null ? psiFile.getVirtualFile() : null;
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
      if (vf == null || !workspaceRoot.isInWorkspace(vf)) {
        presentation.setEnabled(false);
        return;
      }

      QuerySyncManager querySync = QuerySyncManager.getInstance(project);
      if (!querySync.isProjectLoaded() || BlazeSyncStatus.getInstance(project).syncInProgress()) {
        presentation.setEnabled(false);
        return;
      }

      presentation.setEnabled(true);
      DependencyTracker tracker = querySync.getDependencyTracker();
      if (tracker != null) {
        Set<Label> targets = tracker.getPendingTargets(workspaceRoot.relativize(vf));
        if (targets != null && !targets.isEmpty()) {
          String dependency = StringUtil.pluralize("dependency", targets.size());
          presentation.setText(
              String.format("Analysis disabled - missing %d %s ", targets.size(), dependency));
        }
      }
    }

    @Override
    @NotNull
    public JComponent createCustomComponent(
        @NotNull Presentation presentation, @NotNull String place) {
      presentation.setIcon(Actions.Compile);
      presentation.setText("");
      ActionButtonWithText button =
          new ActionButtonWithText(this, presentation, place, JBUI.size(16));
      button.setHorizontalTextPosition(SwingConstants.LEFT);
      button.setFont(
          new FontUIResource(
              button
                  .getFont()
                  .deriveFont(
                      button.getFont().getStyle(),
                      button.getFont().getSize() - JBUIScale.scale(2))));

      return button;
    }
  }
}
