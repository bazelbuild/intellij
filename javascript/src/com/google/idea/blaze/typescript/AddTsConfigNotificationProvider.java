/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.typescript;

import static java.util.stream.Collectors.toSet;

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

class AddTsConfigNotificationProvider
    extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final BoolExperiment addTsConfigNotification =
      new BoolExperiment("add.tsconfig.notification", true);

  private static final Key<EditorNotificationPanel> KEY = Key.create("add.tsconfig.to.project");

  private final Set<File> suppressedFiles = new HashSet<>();

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(
      VirtualFile file, FileEditor fileEditor, Project project) {
    if (!addTsConfigNotification.getValue()) {
      return null;
    }
    if (suppressedFiles.contains(VfsUtil.virtualToIoFile(file))) {
      return null;
    }
    if (!TypeScriptPrefetchFileSource.getTypeScriptExtensions().contains(file.getExtension())) {
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null
        || !projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return null;
    }
    Label tsConfig = getTsConfigLabelForFile(project, file);
    if (tsConfig == null) {
      return null;
    }
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectView == null) {
      return null;
    }
    Set<Label> declaredTsConfigs =
        projectView.getSections(TsConfigRulesSection.KEY).stream()
            .map(ListSection::items)
            .flatMap(Collection::stream)
            .collect(toSet());
    projectView.getScalarValue(TsConfigRuleSection.KEY).ifPresent(declaredTsConfigs::add);
    if (declaredTsConfigs.contains(tsConfig)) {
      return null;
    }
    return createNotificationPanel(project, file, tsConfig);
  }

  private EditorNotificationPanel createNotificationPanel(
      Project project, VirtualFile file, Label tsConfig) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("Do you want to add the tsconfig.json for this file to the project view?");
    panel.createActionLabel(
        "Add tsconfig.json to project view",
        () -> {
          ProjectViewEdit edit =
              ProjectViewEdit.editLocalProjectView(
                  project,
                  builder -> {
                    ListSection<Label> rules = builder.getLast(TsConfigRulesSection.KEY);
                    builder.replace(
                        rules, ListSection.update(TsConfigRulesSection.KEY, rules).add(tsConfig));
                    return true;
                  });
          if (edit != null) {
            edit.apply();
          }
          EditorNotifications.getInstance(project).updateNotifications(file);
        });
    panel.createActionLabel(
        "Hide notification",
        () -> {
          // suppressed for this file until the editor is restarted
          suppressedFiles.add(VfsUtil.virtualToIoFile(file));
          EditorNotifications.getInstance(project).updateNotifications(file);
        });
    return panel;
  }

  @Nullable
  private static Label getTsConfigLabelForFile(Project project, VirtualFile file) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    WorkspacePath directoryPath = getTsConfigDirectoryForFile(root, file);
    if (directoryPath == null) {
      return null;
    }
    VirtualFile directoryVirtualFile =
        VfsUtils.resolveVirtualFile(root.fileForPath(directoryPath), /* refreshIfNeeded= */ false);
    if (directoryVirtualFile == null) {
      return null;
    }
    PsiDirectory directory = PsiManager.getInstance(project).findDirectory(directoryVirtualFile);
    BlazePackage blazePackage = BlazePackage.getContainingPackage(directory);
    if (blazePackage == null) {
      return null;
    }
    Label packageLabel = blazePackage.getPackageLabel();
    if (packageLabel == null) {
      return null;
    }
    return Arrays.stream(blazePackage.buildFile.findChildrenByClass(FuncallExpression.class))
        .filter(e -> Objects.equals(e.getFunctionName(), "ts_config"))
        .map(FuncallExpression::getName)
        .filter(Objects::nonNull)
        .map(packageLabel::withTargetName)
        .filter(Objects::nonNull)
        .findFirst() // there should be at most one
        .orElse(null);
  }

  @Nullable
  private static WorkspacePath getTsConfigDirectoryForFile(WorkspaceRoot root, VirtualFile file) {
    WorkspacePath path = root.workspacePathForSafe(file);
    if (path == null) {
      return null;
    }
    FileOperationProvider fOps = FileOperationProvider.getInstance();
    WorkspacePath parent = path.getParent();
    WorkspacePath tsconfig;
    while (parent != null) {
      tsconfig = new WorkspacePath(parent, "tsconfig.json");
      if (fOps.exists(root.fileForPath(tsconfig))) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }
}
