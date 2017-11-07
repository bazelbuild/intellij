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
package com.google.idea.blaze.ijwb.typescript;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.PlatformUtils;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Supports typescript. */
public class BlazeTypescriptSyncPlugin implements BlazeSyncPlugin {

  // TypeScript support provided by JavaScript plugin
  private static final String TYPESCRIPT_PLUGIN_ID = "JavaScript";

  static final String TSCONFIG_LIBRARY_NAME = "tsconfig$roots";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return PlatformUtils.isIdeaUltimate()
        ? ImmutableSet.of(LanguageClass.TYPESCRIPT)
        : ImmutableSet.of();
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.TYPESCRIPT)
        ? ImmutableList.of(TYPESCRIPT_PLUGIN_ID)
        : ImmutableList.of();
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
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return;
    }

    Set<Label> tsConfigTargets = getTsConfigTargets(projectViewSet);
    if (tsConfigTargets.isEmpty()) {
      invalidProjectViewError(context, Blaze.getBuildSystemProvider(project));
      return;
    }

    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("TsConfig", EventType.BlazeInvocation));
          childContext.output(new StatusOutput("Updating tsconfig..."));

          for (Label target : tsConfigTargets) {
            if (runTsConfigTarget(project, childContext, workspaceRoot, projectViewSet, target)
                != 0) {
              childContext.setHasError();
              // continue running any remaining targets
            }
          }
        });
  }

  private static int runTsConfigTarget(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      Label target) {
    BlazeCommand command =
        BlazeCommand.builder(
                Blaze.getBuildSystemProvider(project).getSyncBinaryPath(), BlazeCommandName.RUN)
            .addTargets(target)
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project, projectViewSet, BlazeCommandName.RUN, BlazeInvocationContext.Sync))
            .build();
    return ExternalTask.builder(workspaceRoot)
        .addBlazeCommand(command)
        .context(context)
        .stderr(
            LineProcessingOutputStream.of(
                BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                    project, context, workspaceRoot)))
        .build()
        .run();
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
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return;
    }

    Library tsConfigLibrary =
        ProjectLibraryTable.getInstance(project).getLibraryByName(TSCONFIG_LIBRARY_NAME);
    if (tsConfigLibrary != null) {
      if (workspaceModifiableModel.findLibraryOrderEntry(tsConfigLibrary) == null) {
        workspaceModifiableModel.addLibraryEntry(tsConfigLibrary);
      }
    }
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return true;
    }
    if (!PluginUtils.isPluginEnabled(TYPESCRIPT_PLUGIN_ID)) {
      IssueOutput.error(
              "The JavaScript plugin is required for TypeScript support. "
                  + "Click here to install/enable the plugin and restart")
          .navigatable(PluginUtils.installOrEnablePluginNavigable(TYPESCRIPT_PLUGIN_ID))
          .submit(context);
      return false;
    }
    return true;
  }

  @Override
  public boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    boolean typescriptActive = workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT);

    if (typescriptActive && !PlatformUtils.isIdeaUltimate()) {
      IssueOutput.error("IntelliJ Ultimate needed for Typescript support.").submit(context);
      return false;
    }

    // Must have either both typescript and ts_config_rules or neither
    if (typescriptActive ^ !getTsConfigTargets(projectViewSet).isEmpty()) {
      invalidProjectViewError(context, Blaze.getBuildSystemProvider(project));
      return false;
    }

    return true;
  }

  private void invalidProjectViewError(
      BlazeContext context, BuildSystemProvider buildSystemProvider) {
    String errorNote =
        "For Typescript support you must add both `additional_languages: typescript`"
            + " and the `ts_config_rules` attribute.";
    String documentationUrl =
        buildSystemProvider.getLanguageSupportDocumentationUrl("dynamic-languages-typescript");
    if (documentationUrl != null) {
      errorNote += String.format("<p>See <a href=\"%1$s\">%1$s</a>.", documentationUrl);
    }
    IssueOutput.error(errorNote).submit(context);
  }

  private static Set<Label> getTsConfigTargets(ProjectViewSet projectViewSet) {
    Optional<Label> oldSectionType = projectViewSet.getScalarValue(TsConfigRuleSection.KEY);
    Set<Label> labels = new LinkedHashSet<>(projectViewSet.listItems(TsConfigRulesSection.KEY));
    oldSectionType.ifPresent(labels::add);
    return labels;
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(TsConfigRuleSection.PARSER, TsConfigRulesSection.PARSER);
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return null;
    }
    return new BlazeTypescriptLibrarySource();
  }
}
