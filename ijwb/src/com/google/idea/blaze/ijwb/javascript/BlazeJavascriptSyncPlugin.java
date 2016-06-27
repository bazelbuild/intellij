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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.SourceTestConfig;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.WebModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * Allows people to use a javascript-only workspace.
 */
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
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.JAVASCRIPT);
  }

  @Override
  public void updateContentEntries(Project project,
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
  public boolean validateProjectView(BlazeContext context,
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
