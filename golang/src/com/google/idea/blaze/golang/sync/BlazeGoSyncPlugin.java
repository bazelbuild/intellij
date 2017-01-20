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
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.GenericSourceFolderProvider;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.golang.sdk.GoSdkUtil;
import com.google.idea.sdkcompat.transactions.Transactions;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NavigatableAdapter;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Supports golang. */
public class BlazeGoSyncPlugin extends BlazeSyncPlugin.Adapter {

  static final String GO_LIBRARY_PREFIX = "GOPATH";
  private static final String GO_MODULE_TYPE_ID = "GO_MODULE";
  private static final String GO_PLUGIN_ID = "ro.redeul.google.go";
  private static final String GO_SDK_TYPE_ID = "Go SDK";

  @Nullable
  @Override
  public ModuleType<?> getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.GO) {
      return ModuleTypeManager.getInstance().findByID(GO_MODULE_TYPE_ID);
    }
    return null;
  }

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.GO);
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.GO) {
      return ImmutableSet.of(LanguageClass.GO);
    }
    return ImmutableSet.of();
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.GO;
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData.workspaceLanguageSettings.isWorkspaceType(WorkspaceType.GO)) {
      return null;
    }
    return GenericSourceFolderProvider.INSTANCE;
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
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.GO)) {
      return;
    }
    for (Library lib : getGoLibraries(project)) {
      if (workspaceModifiableModel.findLibraryOrderEntry(lib) == null) {
        workspaceModifiableModel.addLibraryEntry(lib);
      }
    }
  }

  private static List<Library> getGoLibraries(Project project) {
    List<Library> libraries = Lists.newArrayList();
    LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
    for (Library lib : registrar.getLibraryTable().getLibraries()) {
      if (BlazeGoLibrarySource.isGoLibrary(lib)) {
        libraries.add(lib);
      }
    }

    String moduleLibraryName =
        String.format("%s <%s>", GO_LIBRARY_PREFIX, BlazeDataStorage.WORKSPACE_MODULE_NAME);
    Library goModuleLibrary =
        registrar.getLibraryTable(project).getLibraryByName(moduleLibraryName);
    if (goModuleLibrary != null) {
      libraries.add(goModuleLibrary);
    }
    return libraries;
  }

  /**
   * By default the Go plugin will create duplicate copies of project libraries, one for each
   * module. We only care about library associated with the workspace module.
   */
  static boolean isGoLibraryForModule(Library library, String moduleName) {
    String name = library.getName();
    return name != null && name.equals("GOPATH <" + moduleName + ">");
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.GO)) {
      return null;
    }
    return BlazeGoLibrarySource.INSTANCE;
  }

  @Override
  public boolean validateProjectView(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.GO)) {
      return true;
    }
    if (!isPluginEnabled()) {
      IssueOutput.error("Go plugin needed for Go language support.")
          .navigatable(
              new NavigatableAdapter() {
                @Override
                public void navigate(boolean requestFocus) {
                  if (isPluginInstalled()) {
                    PluginManager.enablePlugin(GO_PLUGIN_ID);
                  } else {
                    PluginsAdvertiser.installAndEnablePlugins(
                        ImmutableSet.of(GO_PLUGIN_ID), EmptyRunnable.INSTANCE);
                  }
                }
              })
          .submit(context);
      return false;
    }
    return true;
  }

  private static boolean isPluginInstalled() {
    return PluginManager.isPluginInstalled(PluginId.getId(GO_PLUGIN_ID));
  }

  private static boolean isPluginEnabled() {
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(GO_PLUGIN_ID));
    return plugin != null && plugin.isEnabled();
  }

  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isWorkspaceType(WorkspaceType.GO)) {
      return;
    }
    Sdk currentSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (currentSdk != null && currentSdk.getSdkType().getName().equals(GO_SDK_TYPE_ID)) {
      return;
    }
    Sdk sdk = getOrCreateGoSdk();
    if (sdk != null) {
      setProjectSdk(project, sdk);
    }
  }

  @Nullable
  private static Sdk getOrCreateGoSdk() {
    ProjectJdkTable sdkTable = ProjectJdkTable.getInstance();
    SdkTypeId type = sdkTable.getSdkTypeByName(GO_SDK_TYPE_ID);
    List<Sdk> sdk = sdkTable.getSdksOfType(type);
    if (!sdk.isEmpty()) {
      return sdk.get(0);
    }
    VirtualFile defaultSdk = GoSdkUtil.suggestSdkDirectory();
    if (defaultSdk != null) {
      return SdkConfigurationUtil.createAndAddSDK(defaultSdk.getPath(), (SdkType) type);
    }
    return null;
  }

  private static void setProjectSdk(Project project, Sdk sdk) {
    Transactions.submitTransactionAndWait(
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(() -> ProjectRootManager.getInstance(project).setProjectSdk(sdk)));
  }
}
