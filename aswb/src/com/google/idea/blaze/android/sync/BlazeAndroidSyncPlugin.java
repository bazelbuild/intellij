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
package com.google.idea.blaze.android.sync;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.intellij.openapi.application.ApplicationManager;
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
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;

/** ASwB sync plugin. */
public class BlazeAndroidSyncPlugin extends BlazeSyncPlugin.Adapter {

  @Nullable
  @Override
  public WorkspaceType getDefaultWorkspaceType() {
    return WorkspaceType.ANDROID;
  }

  @Nullable
  @Override
  public ModuleType getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.ANDROID || workspaceType == WorkspaceType.ANDROID_NDK) {
      return StdModuleTypes.JAVA;
    }
    return null;
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    switch (workspaceType) {
      case ANDROID:
        return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA);
      case ANDROID_NDK:
        if (NdkSupport.NDK_SUPPORT.getValue()) {
          return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA, LanguageClass.C);
        } else {
          return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA);
        }
      default:
        return ImmutableSet.of();
    }
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
      RuleMap ruleMap,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return;
    }

    AndroidSdkPlatform androidSdkPlatform =
        AndroidSdkPlatformSyncer.getAndroidSdkPlatform(project, context);
    BlazeAndroidWorkspaceImporter workspaceImporter =
        new BlazeAndroidWorkspaceImporter(project, context, workspaceRoot, projectViewSet, ruleMap);
    BlazeAndroidImportResult importResult =
        Scope.push(
            context,
            (childContext) -> {
              childContext.push(new TimingScope("AndroidWorkspaceImporter"));
              return workspaceImporter.importWorkspace();
            });
    BlazeAndroidSyncData syncData = new BlazeAndroidSyncData(importResult, androidSdkPlatform);
    syncStateBuilder.put(BlazeAndroidSyncData.class, syncData);
  }

  @Override
  public void updateSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
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
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdkPlatform.androidSdk);
    if (sdk == null) {
      IssueOutput.error(
              String.format("Android platform '%s' not found.", androidSdkPlatform.androidSdk))
          .submit(context);
      return;
    }

    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_1_7);
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
        workspaceRoot,
        projectViewSet,
        blazeProjectData,
        moduleEditor,
        workspaceModule,
        workspaceModifiableModel,
        isAndroidWorkspace(blazeProjectData.workspaceLanguageSettings));
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
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!isAndroidWorkspace(workspaceLanguageSettings)) {
      return true;
    }

    if (workspaceLanguageSettings.isWorkspaceType(WorkspaceType.ANDROID_NDK)
        && !NdkSupport.NDK_SUPPORT.getValue()) {
      IssueOutput.error("Android NDK is not supported yet.").submit(context);
      return false;
    }

    String androidSdkPlatform = projectViewSet.getScalarValue(AndroidSdkPlatformSection.KEY);
    if (Strings.isNullOrEmpty(androidSdkPlatform)) {
      String error =
          Joiner.on('\n')
              .join(
                  "No android_sdk_platform set.",
                  "You should specify the android SDK platform in your '.blazeproject' file.",
                  "To set this add an 'android_sdk_platform' line to your .blazeproject file,",
                  "e.g. 'android_sdk_platform: \"android-N\"', where 'android-N' is a",
                  "platform directory name in your local SDK directory.");
      IssueOutput.error(error)
          .inFile(projectViewSet.getTopLevelProjectViewFile().projectViewFile)
          .submit(context);
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
    return ImmutableList.of(AndroidSdkPlatformSection.PARSER);
  }

  private static boolean isAndroidWorkspace(WorkspaceLanguageSettings workspaceLanguageSettings) {
    return workspaceLanguageSettings.isWorkspaceType(
        WorkspaceType.ANDROID, WorkspaceType.ANDROID_NDK);
  }
}
