/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.libraries;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.actions.BlazeAction;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AddLibraryRuleDirectoryToProjectViewAction extends BlazeAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    Library library = LibraryActionHelper.findLibraryForAction(e);
    if (library != null) {
      addDirectoriesToProjectView(project, ImmutableList.of(library));
    }
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean visible = false;
    boolean enabled = false;
    Project project = e.getProject();
    if (project != null) {
      Library library = LibraryActionHelper.findLibraryForAction(e);
      if (library != null) {
        visible = true;
        if (getDirectoryToAddForLibrary(project, library) != null) {
          enabled = true;
        }
      }
    }
    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
  }

  @Nullable
  static WorkspacePath getDirectoryToAddForLibrary(Project project, Library library) {
    BlazeJarLibrary blazeLibrary =
        LibraryActionHelper.findLibraryFromIntellijLibrary(project, library);
    if (blazeLibrary == null) {
      return null;
    }
    Label originatingRule = blazeLibrary.originatingRule;
    if (originatingRule == null) {
      return null;
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    RuleIdeInfo rule = blazeProjectData.ruleMap.get(originatingRule);
    if (rule == null) {
      return null;
    }
    // To start with, we whitelist only library rules
    // It makes no sense to add directories for java_imports and the like
    if (!rule.kind.isOneOf(Kind.JAVA_LIBRARY, Kind.ANDROID_LIBRARY)) {
      return null;
    }
    if (rule.buildFile == null) {
      return null;
    }
    File buildFile = new File(rule.buildFile.getRelativePath());
    WorkspacePath workspacePath = new WorkspacePath(buildFile.getParent());
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return null;
    }
    boolean exists =
        projectViewSet
            .listItems(DirectorySection.KEY)
            .stream()
            .anyMatch(
                entry ->
                    FileUtil.isAncestor(
                        entry.directory.relativePath(), workspacePath.relativePath(), false));
    if (exists) {
      return null;
    }
    return workspacePath;
  }

  static void addDirectoriesToProjectView(Project project, List<Library> libraries) {
    Set<WorkspacePath> workspacePaths = Sets.newHashSet();
    for (Library library : libraries) {
      WorkspacePath workspacePath = getDirectoryToAddForLibrary(project, library);
      if (workspacePath != null) {
        workspacePaths.add(workspacePath);
      }
    }
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              ListSection<DirectoryEntry> existingSection = builder.getLast(DirectorySection.KEY);
              ListSection.Builder<DirectoryEntry> directoryBuilder =
                  ListSection.update(DirectorySection.KEY, existingSection);
              for (WorkspacePath workspacePath : workspacePaths) {
                directoryBuilder.add(new DirectoryEntry(workspacePath, true));
              }
              builder.replace(existingSection, directoryBuilder);
              return true;
            });
    if (edit == null) {
      Messages.showErrorDialog(
          "Could not modify project view. Check for errors in your project view and try again",
          "Error");
      return;
    }
    edit.apply();
    BlazeSyncManager.getInstance(project)
        .requestProjectSync(
            new BlazeSyncParams.Builder("Adding Library", BlazeSyncParams.SyncMode.INCREMENTAL)
                .addProjectViewTargets(true)
                .addWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
                .build());
  }
}
