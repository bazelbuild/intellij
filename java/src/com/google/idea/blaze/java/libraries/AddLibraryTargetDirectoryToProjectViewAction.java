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
package com.google.idea.blaze.java.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.RuleType;
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
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.WorkspacePathUtil;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import java.io.File;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

class AddLibraryTargetDirectoryToProjectViewAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    Library library = LibraryActionHelper.findLibraryForAction(e);
    if (library != null) {
      addDirectoriesToProjectView(project, ImmutableList.of(library));
    }
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean visible = false;
    boolean enabled = false;
    Library library = LibraryActionHelper.findLibraryForAction(e);
    if (library != null) {
      visible = true;
      if (!getDirectoryToAddForLibrary(project, library).isEmpty()) {
        enabled = true;
      }
    }
    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
  }

  @Nullable
  static ImmutableSet<WorkspacePath> getDirectoryToAddForLibrary(Project project, Library library) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    Set<WorkspacePath> workspacePaths = Sets.newHashSet();
    if (blazeProjectData == null) {
      return ImmutableSet.of();
    }
    ImmutableSet<BlazeJarLibrary> blazeLibraries =
        LibraryActionHelper.findLibraryFromIntellijLibrary(project, blazeProjectData, library);
    if (blazeLibraries.isEmpty()) {
      return ImmutableSet.of();
    }

    for (BlazeJarLibrary blazeJarLibrary : blazeLibraries) {
      TargetKey originatingTarget =
          findOriginatingTargetForLibrary(blazeProjectData, blazeJarLibrary);
      if (originatingTarget == null) {
        continue;
      }
      TargetIdeInfo target = blazeProjectData.getTargetMap().get(originatingTarget);
      if (target == null) {
        continue;
      }
      // To start with, we allow only library rules
      // It makes no sense to add directories for java_imports and the like
      if (!target.getKind().getRuleType().equals(RuleType.LIBRARY)) {
        continue;
      }
      if (target.getBuildFile() == null) {
        continue;
      }
      File buildFile = new File(target.getBuildFile().getRelativePath());
      WorkspacePath workspacePath = new WorkspacePath(Strings.nullToEmpty(buildFile.getParent()));
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      if (projectViewSet == null) {
        continue;
      }
      boolean exists =
          WorkspacePathUtil.isUnderAnyWorkspacePath(
              projectViewSet.listItems(DirectorySection.KEY).stream()
                  .filter(entry -> entry.included)
                  .map(entry -> entry.directory)
                  .collect(toImmutableList()),
              workspacePath);
      if (exists) {
        continue;
      }
      workspacePaths.add(workspacePath);
    }
    return ImmutableSet.copyOf(workspacePaths);
  }

  @Nullable
  private static TargetKey findOriginatingTargetForLibrary(
      BlazeProjectData blazeProjectData, BlazeJarLibrary library) {
    for (TargetIdeInfo target : blazeProjectData.getTargetMap().targets()) {
      JavaIdeInfo javaIdeInfo = target.getJavaIdeInfo();
      if (javaIdeInfo == null) {
        continue;
      }
      if (javaIdeInfo.getJars().contains(library.libraryArtifact)) {
        return target.getKey();
      }
    }
    return null;
  }

  static void addDirectoriesToProjectView(Project project, List<Library> libraries) {
    Set<WorkspacePath> workspacePaths = Sets.newHashSet();
    for (Library library : libraries) {
      workspacePaths.addAll(getDirectoryToAddForLibrary(project, library));
    }
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              ListSection<DirectoryEntry> existingSection = builder.getLast(DirectorySection.KEY);
              ListSection.Builder<DirectoryEntry> directoryBuilder =
                  ListSection.update(DirectorySection.KEY, existingSection);
              for (WorkspacePath workspacePath : workspacePaths) {
                directoryBuilder.add(DirectoryEntry.include(workspacePath));
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
            BlazeSyncParams.builder()
                .setTitle("Adding Library")
                .setSyncMode(SyncMode.INCREMENTAL)
                .setSyncOrigin("AddLibraryTargetDirectoryToProjectViewAction")
                .setAddProjectViewTargets(true)
                .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
                .build());
  }
}
