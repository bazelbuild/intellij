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
package com.google.idea.blaze.ijwb.javascript;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.SourceTestConfig;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.WebModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Allows people to use a javascript-only workspace. */
public class BlazeJavascriptSyncPlugin extends BlazeSyncPlugin.Adapter {

  @Nullable
  @Override
  public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.JAVASCRIPT) {
      return WebModuleType.getInstance();
    }
    return null;
  }

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.JAVASCRIPT);
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.JAVASCRIPT);
  }

  @Override
  public void updateContentEntries(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Collection<ContentEntry> contentEntries) {
    if (!blazeProjectData.workspaceLanguageSettings.isWorkspaceType(WorkspaceType.JAVASCRIPT)) {
      return;
    }

    SourceTestConfig testConfig = new SourceTestConfig(projectViewSet);
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile virtualFile = contentEntry.getFile();
      if (virtualFile == null) {
        continue;
      }
      if (!workspaceRoot.isInWorkspace(virtualFile)) {
        continue;
      }
      WorkspacePath workspacePath = workspaceRoot.workspacePathFor(virtualFile);
      boolean isTestSource = testConfig.isTestSource(workspacePath.relativePath());
      contentEntry.addSourceFolder(virtualFile, isTestSource);
    }
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
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.JAVASCRIPT)
        || BlazeJavascriptLibrarySource.JS_LIBRARY_KIND == null) {
      return;
    }
    for (Library lib : getJavascriptLibraries(project)) {
      if (workspaceModifiableModel.findLibraryOrderEntry(lib) == null) {
        workspaceModifiableModel.addLibraryEntry(lib);
      }
    }
  }

  private static List<Library> getJavascriptLibraries(Project project) {
    List<Library> libraries = Lists.newArrayList();
    LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    for (Library lib : registrar.getLibraryTable().getLibraries()) {
      if (BlazeJavascriptLibrarySource.isJavascriptLibrary(lib)) {
        libraries.add(lib);
      }
    }
    for (Library lib : registrar.getLibraryTable(project).getLibraries()) {
      if (BlazeJavascriptLibrarySource.isJavascriptLibrary(lib)) {
        libraries.add(lib);
      }
    }
    return libraries;
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.JAVASCRIPT)) {
      return null;
    }
    return new BlazeJavascriptLibrarySource();
  }

  @Override
  public boolean validateProjectView(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.JAVASCRIPT)) {
      return true;
    }
    if (!PlatformUtils.isIdeaUltimate()) {
      IssueOutput.error("IntelliJ Ultimate needed for Javascript support.").submit(context);
      return false;
    }
    return true;
  }
}
