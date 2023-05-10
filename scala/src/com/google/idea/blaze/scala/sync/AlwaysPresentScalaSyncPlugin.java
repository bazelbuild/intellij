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
package com.google.idea.blaze.scala.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Unlike most of the scala-specific code, will be run even if the JetBrains scala plugin isn't
 * enabled.
 */
public class AlwaysPresentScalaSyncPlugin implements BlazeSyncPlugin {
  private static final String SCALA_PLUGIN_ID = "org.intellij.scala";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType.equals(WorkspaceType.JAVA)) {
      return ImmutableSet.of(LanguageClass.SCALA);
    }
    return ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.SCALA)
        ? ImmutableList.of(SCALA_PLUGIN_ID)
        : ImmutableList.of();
  }

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.SCALA)) {
      return true;
    }
    if (!PluginUtils.isPluginEnabled(SCALA_PLUGIN_ID)) {
      String msg = "Scala plugin needed for Scala language support.";
      IssueOutput.error(msg)
          .navigatable(PluginUtils.installOrEnablePluginNavigable(SCALA_PLUGIN_ID))
          .submit(context);
      BlazeSyncManager.printAndLogError(msg, context);
      return false;
    }
    return true;
  }
}
