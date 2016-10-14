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
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
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
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.PlatformUtils;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Supports typescript. */
public class BlazeTypescriptSyncPlugin extends BlazeSyncPlugin.Adapter {

  static final String TSCONFIG_LIBRARY_NAME = "tsconfig$roots";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.TYPESCRIPT);
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
      @Nullable SyncState previousSyncState) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return;
    }

    Label tsConfig = projectViewSet.getScalarValue(TsConfigRuleSection.KEY);
    if (tsConfig == null) {
      invalidProjectViewError(context);
      return;
    }

    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("TsConfig"));
          childContext.output(new StatusOutput("Updating tsconfig..."));

          BlazeCommand command =
              BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.RUN)
                  .addTargets(tsConfig)
                  .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet))
                  .build();

          int retVal =
              ExternalTask.builder(workspaceRoot)
                  .addBlazeCommand(command)
                  .context(childContext)
                  .stderr(
                      LineProcessingOutputStream.of(
                          new IssueOutputLineProcessor(project, childContext, workspaceRoot)))
                  .build()
                  .run();

          if (retVal != 0) {
            childContext.setHasError();
          }
        });
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
  public boolean validateProjectView(
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    boolean typescriptActive = workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT);

    if (typescriptActive && !PlatformUtils.isIdeaUltimate()) {
      IssueOutput.error("IntelliJ Ultimate needed for Typescript support.").submit(context);
      return false;
    }

    // Must have either both typescript and ts_config_rule or neither
    Label tsConfig = projectViewSet.getScalarValue(TsConfigRuleSection.KEY);
    if (typescriptActive ^ (tsConfig != null)) {
      invalidProjectViewError(context);
      return false;
    }

    return true;
  }

  private void invalidProjectViewError(BlazeContext context) {
    IssueOutput.error(
            "For Typescript support you must add both additional_languages: "
                + "typescript and the ts_config_rule attribute.")
        .submit(context);
  }

  @Override
  public Collection<SectionParser> getSections() {
    return ImmutableList.of(TsConfigRuleSection.PARSER);
  }
}
