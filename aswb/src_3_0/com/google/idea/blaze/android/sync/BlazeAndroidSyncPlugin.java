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
package com.google.idea.blaze.android.sync;

import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.android.projectview.AndroidMinSdkSection;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.java.sync.JavaLanguageLevelHelper;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.projectstructure.JavaSourceFolderProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** ASwB sync plugin. */
public class BlazeAndroidSyncPlugin implements BlazeSyncPlugin {

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.ANDROID);
  }

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.ANDROID;
  }

  @Nullable
  @Override
  public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.ANDROID) {
      return StdModuleTypes.JAVA;
    }
    return null;
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType != WorkspaceType.ANDROID) {
      return ImmutableSet.of();
    }
    if (NdkSupport.NDK_SUPPORT.getValue()) {
      return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA, LanguageClass.C);
    } else {
      return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA);
    }
  }

  @Override
  public void installSdks(BlazeContext context) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    File path = IdeSdks.getInstance().getAndroidSdkPath();
    if (path != null) {
      context.output(new StatusOutput("Installing SDK platforms..."));
      ApplicationManager.getApplication()
          .invokeAndWait(
              () -> {
                IdeSdks.getInstance().createAndroidSdkPerAndroidTarget(path);
              },
              ModalityState.defaultModalityState());
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
      BlazeVersionData blazeVersionData,
      @Nullable WorkingSet workingSet,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return;
    }

    AndroidSdkPlatform androidSdkPlatform =
        AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet);

    JavaSourceFilter sourceFilter =
        new JavaSourceFilter(project, workspaceRoot, projectViewSet, targetMap);

    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(
            project,
            context,
            workspaceRoot,
            projectViewSet,
            targetMap,
            sourceFilter,
            artifactLocationDecoder);
    BlazeAndroidImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(new TimingScope("AndroidWorkspaceImporter", EventType.Other));
              return workspaceImporter.importWorkspace();
            });
    BlazeAndroidSyncData syncData = new BlazeAndroidSyncData(importResult, androidSdkPlatform);
    syncStateBuilder.put(BlazeAndroidSyncData.class, syncData);
  }

  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!isAndroidWorkspace(blazeProjectData.workspaceLanguageSettings)) {
      return;
    }
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return;
    }
    AndroidSdkPlatform androidSdkPlatform = syncData.androidSdkPlatform;
    if (androidSdkPlatform == null) {
      return;
    }
    Sdk sdk = BlazeSdkProvider.getInstance().findSdk(androidSdkPlatform.androidSdk);
    if (sdk == null) {
      IssueOutput.error(
              String.format("Android platform '%s' not found.", androidSdkPlatform.androidSdk))
          .submit(context);
      return;
    }

    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelHelper.getJavaLanguageLevel(
            projectViewSet, blazeProjectData, LanguageLevel.JDK_1_8);
    setProjectSdkAndLanguageLevel(project, sdk, javaLanguageLevel);
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
    BlazeAndroidProjectStructureSyncer.updateProjectStructure(
        project,
        context,
        projectViewSet,
        blazeProjectData,
        moduleEditor,
        workspaceModule,
        workspaceModifiableModel,
        isAndroidWorkspace(blazeProjectData.workspaceLanguageSettings));
  }

  @Override
  public void updateInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule) {
    BlazeAndroidProjectStructureSyncer.updateInMemoryState(
        project,
        workspaceRoot,
        projectViewSet,
        blazeProjectData,
        workspaceModule,
        isAndroidWorkspace(blazeProjectData.workspaceLanguageSettings));
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData.workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID)) {
      return null;
    }
    return new JavaSourceFolderProvider(projectData.syncState.get(BlazeJavaSyncData.class));
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!isAndroidWorkspace(blazeProjectData.workspaceLanguageSettings)) {
      return true;
    }

    boolean valid = true;
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.requiresAndroidModel() && facet.getAndroidModel() == null) {
        IssueOutput.error("Android model missing for module: " + module.getName()).submit(context);
        valid = false;
      }
    }
    return valid;
  }

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return true;
    }

    if (workspaceLanguageSettings.isLanguageActive(LanguageClass.C)
        && !NdkSupport.NDK_SUPPORT.getValue()) {
      IssueOutput.error("Android NDK is not supported yet.").submit(context);
      return false;
    }

    if (AndroidSdkFromProjectView.getAndroidSdkPlatform(context, projectViewSet) == null) {
      return false;
    }
    return true;
  }

  private static void setProjectSdkAndLanguageLevel(
      final Project project, final Sdk sdk, final LanguageLevel javaLanguageLevel) {
    UIUtil.invokeAndWaitIfNeeded(
        (Runnable)
            () ->
                ApplicationManager.getApplication()
                    .runWriteAction(
                        () -> {
                          ProjectRootManagerEx rootManager =
                              ProjectRootManagerEx.getInstanceEx(project);
                          rootManager.setProjectSdk(sdk);
                          LanguageLevelProjectExtension ext =
                              LanguageLevelProjectExtension.getInstance(project);
                          ext.setLanguageLevel(javaLanguageLevel);
                        }));
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(
        AndroidMinSdkSection.PARSER,
        AndroidSdkPlatformSection.PARSER,
        GeneratedAndroidResourcesSection.PARSER);
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!isAndroidWorkspace(blazeProjectData.workspaceLanguageSettings)) {
      return null;
    }
    return new BlazeAndroidLibrarySource(blazeProjectData);
  }

  private static boolean isAndroidWorkspace(WorkspaceLanguageSettings workspaceLanguageSettings) {
    return workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID);
  }
}
