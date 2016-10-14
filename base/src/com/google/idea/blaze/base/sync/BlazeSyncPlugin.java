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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Can plug into the blaze sync system. */
public interface BlazeSyncPlugin {
  ExtensionPointName<BlazeSyncPlugin> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncPlugin");

  /**
   * May be used by the plugin to create/edit modules.
   *
   * <p>Using this ensures that the blaze plugin is aware of the modules, won't garbage collect
   * them, and that all module modifications happen in a single transaction.
   */
  interface ModuleEditor {
    /** Creates a new module and registers it with the module editor. */
    Module createModule(String moduleName, ModuleType moduleType);

    /**
     * Edits a module. It will be committed when commit is called.
     *
     * <p>The module will be returned in a cleared state. You should not call this method multiple
     * times.
     */
    ModifiableRootModel editModule(Module module);

    /**
     * Registers a module. This prevents garbage collection of the module upon commit.
     *
     * @return True if the module exists and was registered.
     */
    boolean registerModule(String moduleName);

    /** Finds a module by name. This doesn't register the module. */
    @Nullable
    Module findModule(String moduleName);

    /** Commits the module editor without garbage collection. */
    void commit();
  }

  /** @return The default workspace type recommended by this plugin. */
  @Nullable
  WorkspaceType getDefaultWorkspaceType();

  /** @return The module type for the workspace given the workspace type. */
  @Nullable
  ModuleType getWorkspaceModuleType(WorkspaceType workspaceType);

  /** @return The set of supported languages under this workspace type. */
  Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType);

  /** Given the rule map, update the sync state for this plugin. Should not have side effects. */
  void updateSyncState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      BlazeRoots blazeRoots,
      @Nullable WorkingSet workingSet,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      RuleMap ruleMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState);

  /** Updates the sdk. */
  void updateSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData);

  /**
   * Modify the project content entries. There will be one content entry per project directory from
   * the project view set.
   */
  void updateContentEntries(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Collection<ContentEntry> contentEntries);

  /** Modifies the IDE project structure in accordance with the sync data. */
  void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel);

  /** Validates the project. */
  boolean validate(Project project, BlazeContext context, BlazeProjectData blazeProjectData);

  /**
   * Validates the project view.
   *
   * @return True for success, false for fatal error.
   */
  boolean validateProjectView(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings);

  /** Returns any custom sections that this plugin supports. */
  Collection<SectionParser> getSections();

  /** Returns whether this plugin requires resolving ide artifacts to function. */
  boolean requiresResolveIdeArtifacts();

  /** Convenience adapter to help stubbing out methods. */
  class Adapter implements BlazeSyncPlugin {

    @Nullable
    @Override
    public WorkspaceType getDefaultWorkspaceType() {
      return null;
    }

    @Nullable
    @Override
    public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
      return null;
    }

    @Override
    public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
      return ImmutableSet.of();
    }

    @Override
    public void updateSyncState(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        BlazeRoots blazeRoots,
        @Nullable WorkingSet workingSet,
        WorkspacePathResolver workspacePathResolver,
        ArtifactLocationDecoder artifactLocationDecoder,
        RuleMap ruleMap,
        SyncState.Builder syncStateBuilder,
        @Nullable SyncState previousSyncState) {}

    @Override
    public void updateSdk(
        Project project,
        BlazeContext context,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData) {}

    @Override
    public void updateContentEntries(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        Collection<ContentEntry> contentEntries) {}

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
        ModifiableRootModel workspaceModifiableModel) {}

    @Override
    public boolean validate(
        Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
      return true;
    }

    @Override
    public boolean validateProjectView(
        BlazeContext context,
        ProjectViewSet projectViewSet,
        WorkspaceLanguageSettings workspaceLanguageSettings) {
      return true;
    }

    @Override
    public Collection<SectionParser> getSections() {
      return ImmutableList.of();
    }

    @Override
    public boolean requiresResolveIdeArtifacts() {
      return false;
    }
  }
}
