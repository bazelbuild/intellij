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
package com.google.idea.blaze.golang.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewSet.ProjectViewFile;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.util.PlatformUtils;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Unlike most of the go-specific code, will be run even if the JetBrains Go plugin isn't enabled.
 */
public class AlwaysPresentGoSyncPlugin implements BlazeSyncPlugin {

  private static final String GO_PLUGIN_ID = "org.jetbrains.plugins.go";
  private static final String OLD_GO_PLUGIN_ID = "ro.redeul.google.go";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return isGoPluginSupported() ? ImmutableSet.of(LanguageClass.GO) : ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.GO)
        ? ImmutableList.of(GO_PLUGIN_ID)
        : ImmutableList.of();
  }

  /** Go plugin is only supported in IJ UE and GoLand. */
  private static boolean isGoPluginSupported() {
    return PlatformUtils.isGoIde()
        || PlatformUtils.isIdeaUltimate()
        || ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)
        || PluginUtils.isPluginEnabled(GO_PLUGIN_ID)) {
      return true;
    }
    if (PluginUtils.isPluginEnabled(OLD_GO_PLUGIN_ID)) {
      String error =
          String.format(
              "The currently installed Go plugin is no longer supported by the %s plugin.\n"
                  + "Click here to install the new JetBrains Go plugin and restart.",
              Blaze.defaultBuildSystemName());
      IssueOutput.error(error)
          .withNavigatable(
              new NavigatableAdapter() {
                @Override
                public void navigate(boolean requestFocus) {
                  PluginManager.disablePlugin(OLD_GO_PLUGIN_ID);
                  PluginUtils.installOrEnablePlugin(GO_PLUGIN_ID);
                }
              })
          .submit(context);
      return true;
    }
    IssueOutput.error(
            "Go support requires the Go plugin. Click here to install/enable the JetBrains Go "
                + "plugin, then restart the IDE")
        .withNavigatable(PluginUtils.installOrEnablePluginNavigable(GO_PLUGIN_ID))
        .submit(context);
    return true;
  }

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (workspaceLanguageSettings.isLanguageActive(LanguageClass.GO) && !isGoPluginSupported()) {
      String msg =
          String.format(
              "Go is no longer supported by the %s plugin with IntelliJ Community Edition.\n"
                  + "Please install Ultimate Edition and upgrade to the JetBrains Go plugin",
              Blaze.defaultBuildSystemName());
      IssueOutput.error(msg).submit(context);
      BlazeSyncManager.printAndLogError(msg, context);
      return false;
    }
    if (goWorkspaceTypeSupported()
        || !workspaceLanguageSettings.isWorkspaceType(WorkspaceType.GO)) {
      return true;
    }
    ProjectViewFile topLevelProjectViewFile = projectViewSet.getTopLevelProjectViewFile();
    String msg =
        "Go workspace_type is no longer supported. Please add 'go' to "
            + "additional_languages instead";
    boolean fixable =
        project != null
            && topLevelProjectViewFile != null
            && topLevelProjectViewFile.projectView.getScalarValue(WorkspaceTypeSection.KEY)
                == WorkspaceType.GO;
    BlazeSyncManager.printAndLogError(msg, context);
    msg += fixable ? ". Click here to fix your .blazeproject and resync." : ", then resync.";
    IssueOutput.error(msg)
        .withNavigatable(
            !fixable
                ? null
                : new NavigatableAdapter() {
                  @Override
                  public void navigate(boolean requestFocus) {
                    fixLanguageSupport(project);
                  }
                })
        .submit(context);
    return false;
  }

  private static void fixLanguageSupport(Project project) {
    ProjectViewEdit edit =
        ProjectViewEdit.editLocalProjectView(
            project,
            builder -> {
              removeGoWorkspaceType(builder);
              addToAdditionalLanguages(builder);
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
        .incrementalProjectSync(/* reason= */ "enabled-go-support");
  }

  private static boolean goWorkspaceTypeSupported() {
    return PlatformUtils.isGoIde();
  }

  private static void removeGoWorkspaceType(ProjectView.Builder builder) {
    if (goWorkspaceTypeSupported()) {
      return;
    }
    ScalarSection<WorkspaceType> section = builder.getLast(WorkspaceTypeSection.KEY);
    if (section != null && section.getValue() == WorkspaceType.GO) {
      builder.remove(section);
    }
  }

  private static void addToAdditionalLanguages(ProjectView.Builder builder) {
    ListSection<LanguageClass> existingSection = builder.getLast(AdditionalLanguagesSection.KEY);
    builder.replace(
        existingSection,
        ListSection.update(AdditionalLanguagesSection.KEY, existingSection).add(LanguageClass.GO));
  }
}
