/*
 * Copyright 2019-2024 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.idea.blaze.base.issueparser.BlazeIssueParser.targetDetectionQueryParsers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.dependencies.DirectoryToTargetProvider;
import com.google.idea.blaze.base.dependencies.SourceToTargetFilteringStrategy;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TargetTagFilter;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.EnablePythonCodegenSupport;
import com.google.idea.blaze.base.projectview.section.sections.SyncManualTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/** Derives sync targets from the project directories. */
public final class SyncProjectTargetsHelper {

  private SyncProjectTargetsHelper() {}

  /** The full set of project targets which should be built during sync. */
  public static class ProjectTargets {
    final ImmutableList<TargetExpression> derivedTargets;
    final ImmutableList<TargetExpression> explicitTargets;

    private ProjectTargets(
        ImmutableList<TargetExpression> derivedTargets,
        ImmutableList<TargetExpression> explicitTargets) {
      this.derivedTargets = derivedTargets;
      this.explicitTargets = explicitTargets;
    }

    public ImmutableList<TargetExpression> getTargetsToSync() {
      // add explicit targets after derived targets so users can override automatic behavior
      return ImmutableList.<TargetExpression>builder()
          .addAll(derivedTargets)
          .addAll(explicitTargets)
          .build();
    }
  }

  public static ProjectTargets getProjectTargets(
      Project project,
      BlazeContext context,
      ProjectViewSet viewSet,
      WorkspacePathResolver pathResolver,
      WorkspaceLanguageSettings languageSettings)
      throws SyncFailedException, SyncCanceledException, ExecutionException, InterruptedException {
    ImmutableList<TargetExpression> derived =
        shouldDeriveSyncTargetsFromDirectories(viewSet)
            ? deriveTargetsFromDirectories(
                project, context, viewSet, pathResolver, languageSettings)
            : ImmutableList.of();
    List<TargetExpression> projectViewTargets = viewSet.listItems(TargetSection.KEY);
    return new ProjectTargets(derived, ImmutableList.copyOf(projectViewTargets));
  }

  private static boolean shouldDeriveSyncTargetsFromDirectories(ProjectViewSet viewSet) {
    return viewSet.getScalarValue(AutomaticallyDeriveTargetsSection.KEY).orElse(false);
  }

  public static boolean shouldSyncManualTargets(ProjectViewSet viewSet) {
    return viewSet.getScalarValue(SyncManualTargetsSection.KEY).orElse(false);
  }

  private static ImmutableList<TargetExpression> deriveTargetsFromDirectories(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      WorkspaceLanguageSettings languageSettings)
      throws SyncFailedException, SyncCanceledException, ExecutionException, InterruptedException {
    String fileBugSuggestion =
        Blaze.getBuildSystemName(project) == BuildSystemName.Bazel
            ? ""
            : " Please run 'Blaze > File a Bug'";
    if (!DirectoryToTargetProvider.hasProvider()) {
      IssueOutput.error(
              "Can't derive targets from project directories: no query provider available."
                  + fileBugSuggestion)
          .submit(context);
      throw new SyncFailedException();
    }
    ImportRoots importRoots = ImportRoots.builder(project).add(projectViewSet).build();
    if (importRoots.rootDirectories().isEmpty()) {
      return ImmutableList.of();
    }
    String title = "Query targets in project directories";
    List<TargetInfo> targets =
        ProgressiveTaskWithProgressIndicator.builder(project, title).submitTaskWithResult(indicator ->
            Scope.push(
                context,
                childContext -> {
                  childContext.push(
                      new TimingScope("QueryDirectoryTargets", EventType.BlazeInvocation));
                  childContext.output(new StatusOutput("Querying targets in project directories..."));
                  var scope = childContext.getScope(ToolWindowScope.class);
                  if (scope != null) { // If ToolWindowScope doesn't already exist, it means the output is not supposed to be printed to toolwindow (for example in tests)
                    var task = new Task(project, "Query targets in project directories", Task.Type.SYNC, scope.getTask());
                    var newScope = new ToolWindowScope.Builder(project, task)
                        .setProgressIndicator(indicator)
                        .setPopupBehavior(BlazeUserSettings.FocusBehavior.ON_ERROR)
                        .setIssueParsers(targetDetectionQueryParsers(project, WorkspaceRoot.fromProject(project)))
                        .build();
                    childContext.push(newScope);
                  }
                  // We don't want blaze build errors to fail the whole sync
                  childContext.setPropagatesErrors(false);
                  return DirectoryToTargetProvider.expandDirectoryTargets(
                      project, shouldSyncManualTargets(projectViewSet), importRoots, pathResolver, childContext);
                })).get(); // We still call no-timeout waitFor in ExternalTask.run()

    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }

    if (targets == null) {
      IssueOutput.error("Deriving targets from project directories failed." + fileBugSuggestion)
          .submit(context);
      throw new SyncFailedException();
    }

    // retainedByKind will contain the targets which are to be kept because their Kind matches
    // one of the languages actively in use in the IDE.

    ImmutableList<TargetExpression> retainedByKind =
        SourceToTargetFilteringStrategy.filterTargets(targets).stream()
            .filter(
                t ->
                    t.getKind() != null
                        && t.getKind().getLanguageClasses().stream()
                            .anyMatch(languageSettings::isLanguageActive))
            .map(t -> t.label)
            .collect(toImmutableList());

    // Gather together those targets that are rejected. Run the rejected targets through another
    // Bazel query to see if they are code-generation (code-gen) ones. If any of them are then we
    // should include those as well. In such cases the rule name might be something like
    // `my_code_gen` which will not be detected as a library for example.

    List<TargetExpression> rejectedByKind = targets.stream()
        .map(TargetInfo::getLabel)
        .filter(label -> !retainedByKind.contains(label))
        .collect(Collectors.toUnmodifiableList());

    List<TargetExpression> retainedByCodeGen = ImmutableList.of();

    if (!rejectedByKind.isEmpty() && TargetTagFilter.hasProvider()) {
      Set<String> activeLanguageCodeGeneratorTags = languageSettings.getActiveLanguages()
          .stream()
          .map(LanguageClass::getCodeGeneratorTag)
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());

      if (!activeLanguageCodeGeneratorTags.isEmpty() && projectViewSet.getScalarValue(EnablePythonCodegenSupport.KEY).orElse(false)) {
        retainedByCodeGen = TargetTagFilter.filter(
            project,
            context,
            rejectedByKind,
            activeLanguageCodeGeneratorTags);
      }
    }

    ImmutableList<TargetExpression> retained = ImmutableList.<TargetExpression>builder()
        .addAll(retainedByKind)
        .addAll(retainedByCodeGen)
        .build();

    context.output(
        PrintOutput.log(
            String.format(
                "%d targets found under project directories; syncing %d of them",
                targets.size(), retained.size())));

    return retained;
  }
}
