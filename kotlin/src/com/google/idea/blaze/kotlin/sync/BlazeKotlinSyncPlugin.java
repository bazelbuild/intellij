/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.SyncState.Builder;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.kotlin.sync.importer.BlazeKotlinWorkspaceImporter;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinImportResult;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinSyncData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import org.jetbrains.kotlin.idea.framework.CommonLibraryType;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Supports Kotlin.
 */
public class BlazeKotlinSyncPlugin extends BlazeSyncPlugin.Adapter {
    @Override
    public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
        if (workspaceType.equals(WorkspaceType.JAVA)) {
            return ImmutableSet.of(LanguageClass.KOTLIN);
        }
        return ImmutableSet.of();
    }

    // TODO replace library type.
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
        if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
            return;
        }
        for (Library library : ProjectLibraryTable.getInstance(project).getLibraries()) {
            // Convert the type of the SDK library to prevent the kotlin plugin from
            // showing the missing SDK notification.
            if (library.getName() != null && library.getName().startsWith("kotlin-stdlib")) {
                ExistingLibraryEditor editor = new ExistingLibraryEditor(library, null);
                editor.setType(CommonLibraryType.INSTANCE);
                editor.commit();
                return;
            }
        }
    }

    @Override
    public void updateSyncState(
            Project project,
            BlazeContext context,
            WorkspaceRoot workspaceRoot,
            ProjectViewSet projectViewSet,
            WorkspaceLanguageSettings workspaceLanguageSettings,
            BlazeInfo blazeInfo,
            @Nullable WorkingSet workingSet,
            WorkspacePathResolver workspacePathResolver,
            ArtifactLocationDecoder artifactLocationDecoder,
            TargetMap targetMap,
            Builder syncStateBuilder,
            @Nullable SyncState previousSyncState) {
        if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
            return;
        }
        BlazeKotlinWorkspaceImporter blazeKotlinWorkspaceImporter =
                new BlazeKotlinWorkspaceImporter(project, workspaceRoot, projectViewSet, targetMap);
        BlazeKotlinImportResult importResult =
                Scope.push(
                        context,
                        (childContext) -> {
                            childContext.push(new TimingScope("KotlinWorkspaceImporter"));
                            return blazeKotlinWorkspaceImporter.importWorkspace();
                        });
        BlazeKotlinSyncData syncData = new BlazeKotlinSyncData(importResult);
        syncStateBuilder.put(BlazeKotlinSyncData.class, syncData);
    }

    @Nullable
    @Override
    public LibrarySource getLibrarySource(
            ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
        if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
            return null;
        }
        return new BlazeKotlinLibrarySource(blazeProjectData);
    }
}
