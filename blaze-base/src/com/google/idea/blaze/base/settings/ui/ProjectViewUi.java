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
package com.google.idea.blaze.base.settings.ui;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewFileType;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * UI for changing the ProjectView.
 */
public class ProjectViewUi {

  private static final String USE_SHARED_PROJECT_VIEW = "Use shared project view file";

  private final Disposable parentDisposable;
  private EditorEx projectViewEditor;
  private JCheckBox useShared;

  private WorkspaceRoot workspaceRoot;
  private boolean useSharedProjectView;
  private boolean allowEditShared;
  private String sharedProjectViewText;
  private boolean settingsInitialized;

  public ProjectViewUi(Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  /**
   * To support the custom language features, we need a ProjectImpl, and it's not desirable to create one from scratch.<br>
   * @return the current, non-default project, if one exists, else the default project.
   */
  public static Project getProject() {
    Project project = (Project) DataManager.getInstance().getDataContext().getData("project");
    if (project != null && project instanceof ProjectImpl) {
      return project;
    }
    return ProjectManager.getInstance().getDefaultProject();
  }

  public static Dimension getMinimumSize() {
    return new Dimension(1000, 550);
  }

  private static EditorEx createEditor(String tooltip) {
    Project project = getProject();
    LightVirtualFile virtualFile = new LightVirtualFile("mockProjectViewFile", ProjectViewLanguage.INSTANCE, "");
    final Document document = ((EditorFactoryImpl) EditorFactory.getInstance()).createDocument(true);
    ((DocumentImpl) document).setAcceptSlashR(true);
    FileDocumentManagerImpl.registerDocument(document, virtualFile);

    FileManager fileManager = ((PsiManagerEx) PsiManager.getInstance(project)).getFileManager();
    fileManager.setViewProvider(virtualFile, fileManager.createFileViewProvider(virtualFile, true));

    if (project.isDefault()) {
      // Undo-redo doesn't work with the default project. Explicitly turn it off to avoid error dialogs.
      UndoUtil.disableUndoFor(document);
    }

    EditorEx editor = (EditorEx) EditorFactory.getInstance().createEditor(document, project, ProjectViewFileType.INSTANCE, false);
    final EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setRightMarginShown(false);
    settings.setAdditionalPageAtBottom(false);
    editor.getComponent().setMinimumSize(getMinimumSize());
    editor.getComponent().setPreferredSize(getMinimumSize());
    editor.getComponent().setToolTipText(tooltip);
    editor.getComponent().setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
    editor.getComponent().setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
    return editor;
  }

  public void fillUi(JPanel canvas, int indentLevel) {
    String tooltip = "Enter a project view descriptor file. See 'go/intellij/docs/project-views.md' for more information.";

    projectViewEditor = createEditor(tooltip);
    projectViewEditor.getColorsScheme().setColor(EditorColors.READONLY_BACKGROUND_COLOR, UIManager.getColor("Label.background"));
    Disposer.register(parentDisposable, () -> EditorFactory.getInstance().releaseEditor(projectViewEditor));

    JBLabel labelsLabel = new JBLabel("Project View");
    labelsLabel.setToolTipText(tooltip);
    canvas.add(labelsLabel, UiUtil.getFillLineConstraints(indentLevel));

    canvas.add(projectViewEditor.getComponent(), UiUtil.getFillLineConstraints(indentLevel));

    useShared = new JCheckBox(USE_SHARED_PROJECT_VIEW);
    useShared.addActionListener(e -> {
      useSharedProjectView = useShared.isSelected();
      if (useSharedProjectView) {
        setProjectViewText(sharedProjectViewText);
      }
      updateTextAreasEnabled();
    });
    canvas.add(useShared, UiUtil.getFillLineConstraints(indentLevel));
  }

  public void init(
    WorkspaceRoot workspaceRoot,
    String projectViewText,
    @Nullable String sharedProjectViewText,
    @Nullable File sharedProjectViewFile,
    boolean useSharedProjectView,
    boolean allowEditShared
  ) {
    this.workspaceRoot = workspaceRoot;
    this.useSharedProjectView = useSharedProjectView;
    this.allowEditShared = allowEditShared;
    this.sharedProjectViewText = sharedProjectViewText;

    assert !(useSharedProjectView && sharedProjectViewText == null);

    if (sharedProjectViewFile != null) {
      WorkspacePath workspacePath = workspaceRoot.workspacePathFor(sharedProjectViewFile);
      useShared.setText(USE_SHARED_PROJECT_VIEW + ": " + workspacePath.relativePath());
    }

    useShared.setSelected(useSharedProjectView);

    if (sharedProjectViewText == null) {
      useShared.setEnabled(false);
    }

    setDummyWorkspacePathResolverProvider(workspaceRoot);
    setProjectViewText(projectViewText);
    settingsInitialized = true;
  }

  private void setDummyWorkspacePathResolverProvider(WorkspaceRoot workspaceRoot) {
    MutablePicoContainer container = (MutablePicoContainer) getProject().getPicoContainer();
    Class<WorkspacePathResolverProvider> key = WorkspacePathResolverProvider.class;
    Object oldProvider = container.getComponentInstance(key);
    container.unregisterComponent(key.getName());
    container.registerComponentInstance(key.getName(),
                                        (WorkspacePathResolverProvider) () -> new WorkspacePathResolverImpl(workspaceRoot));
    if (!settingsInitialized) {
      Disposer.register(parentDisposable, () -> {
        container.unregisterComponent(key.getName());
        if (oldProvider != null) {
          container.registerComponentInstance(key.getName(), oldProvider);
        }
      });
    }
  }

  private void setProjectViewText(String projectViewText) {
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        projectViewEditor.getDocument().setReadOnly(false);
        projectViewEditor.getDocument().setText(projectViewText);
      }
    }.execute();
    updateTextAreasEnabled();
  }

  private void updateTextAreasEnabled() {
    boolean editEnabled = allowEditShared || !useSharedProjectView;
    projectViewEditor.setViewer(!editEnabled);
    projectViewEditor.getDocument().setReadOnly(!editEnabled);
    projectViewEditor.reinitSettings();
  }

  public ProjectViewSet parseProjectView(final List<IssueOutput> issues) {
    final String projectViewText = projectViewEditor.getDocument().getText();
    final OutputSink<IssueOutput> issueCollector = output -> {
      issues.add(output);
      return OutputSink.Propagation.Continue;
    };
    return Scope.root(context -> {
      context.addOutputSink(IssueOutput.class, issueCollector);
      ProjectViewParser projectViewParser = new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
      projectViewParser.parseProjectView(projectViewText);
      return projectViewParser.getResult();
    });
  }

  public boolean getUseSharedProjectView() {
    return this.useSharedProjectView;
  }

}
