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
package com.google.idea.blaze.golang.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.GenericSourceFolderProvider;
import com.google.idea.blaze.base.sync.RefreshRequestType;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.golang.resolve.BlazeGoRootsProvider;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Supports golang. */
public class BlazeGoSyncPlugin implements BlazeSyncPlugin {

  /** From {@link com.goide.inspections.WrongSdkConfigurationNotificationProvider}. */
  private static final String DO_NOT_SHOW_NOTIFICATION_ABOUT_EMPTY_GOPATH =
      "DO_NOT_SHOW_NOTIFICATION_ABOUT_EMPTY_GOPATH";

  private static final BoolExperiment refreshExecRoot =
      new BoolExperiment("refresh.exec.root.golang", true);

  static final ImmutableSet<String> GO_LIBRARY_PREFIXES = ImmutableSet.of("GOPATH", "Go SDK");

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.GO);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return PlatformUtils.isGoIde() ? WorkspaceType.GO : null;
  }

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return PlatformUtils.isGoIde() ? ImmutableList.of(WorkspaceType.GO) : ImmutableList.of();
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData.getWorkspaceLanguageSettings().isWorkspaceType(WorkspaceType.GO)) {
      return null;
    }
    return GenericSourceFolderProvider.INSTANCE;
  }

  @Nullable
  @Override
  public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
    return workspaceType == WorkspaceType.GO
        ? ModuleTypeManager.getInstance().getDefaultModuleType()
        : null;
  }

  @Override
  public void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)) {
      return;
    }
    for (Library lib : getGoLibraries(project)) {
      if (workspaceModifiableModel.findLibraryOrderEntry(lib) == null) {
        workspaceModifiableModel.addLibraryEntry(lib);
      }
    }
    BlazeGoRootsProvider.handleGoSymlinks(context, project, blazeProjectData);
    PropertiesComponent.getInstance().setValue(DO_NOT_SHOW_NOTIFICATION_ABOUT_EMPTY_GOPATH, true);
  }

  private static List<Library> getGoLibraries(Project project) {
    List<Library> libraries = Lists.newArrayList();
    LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    for (Library lib : registrar.getLibraryTable().getLibraries()) {
      if (BlazeGoLibrarySource.isGoLibrary(lib)) {
        libraries.add(lib);
      }
    }

    for (Library lib : registrar.getLibraryTable(project).getLibraries()) {
      if (BlazeGoLibrarySource.isGoLibrary(lib)) {
        libraries.add(lib);
      }
    }
    return libraries;
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)) {
      return null;
    }
    return BlazeGoLibrarySource.INSTANCE;
  }

  @Override
  public ImmutableSetMultimap<RefreshRequestType, VirtualFile> filesToRefresh(
      BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)) {
      return ImmutableSetMultimap.of();
    }
    if (!refreshExecRoot.getValue()) {
      return ImmutableSetMultimap.of();
    }
    // recursive refresh of the blaze execution root. This is required because our blaze aspect
    // can't yet tell us exactly which genfiles are required to resolve the project.
    VirtualFile execRoot =
        VfsUtils.resolveVirtualFile(blazeProjectData.getBlazeInfo().getExecutionRoot());
    if (execRoot == null) {
      return ImmutableSetMultimap.of();
    }
    return ImmutableSetMultimap.of(
        RefreshRequestType.create(/* recursive= */ true, /* reloadChildren= */ true), execRoot);
  }
}
