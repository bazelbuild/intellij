/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.gazelle;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.model.primitives.InvalidTargetException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncScope;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.toolwindow.Task;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class GazelleSyncListener implements SyncListener {

  private Optional<Label> getGazelleBinary(@Nullable ProjectViewSet projectViewSet)
      throws InvalidTargetException {
    if (projectViewSet == null) {
      return Optional.empty();
    }

    Optional<Label> gazelleBinaryFromProjectSettings =
        projectViewSet.getScalarValue(GazelleSection.KEY);
    if (gazelleBinaryFromProjectSettings.isPresent()) {
      return gazelleBinaryFromProjectSettings;
    }
    GazelleUserSettings settings = GazelleUserSettings.getInstance();
    return settings.getGazelleTargetLabel();
  }

  private boolean runGazelleHeadless() {
    return GazelleUserSettings.getInstance().shouldRunHeadless();
  }

  private GazelleRunResult doRunGazelle(
      Project project,
      BlazeContext context,
      Label gazelleBinary,
      WorkspaceRoot workspaceRoot,
      ImmutableList<Parser> issueParsers,
      Collection<WorkspacePath> importantDirectories,
      List<String> blazeFlags) {
    BuildSystem.BuildInvoker invoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);

    return GazelleRunner.getInstance()
        .runBlazeGazelle(
            context,
            invoker,
            workspaceRoot,
            blazeFlags,
            gazelleBinary,
            importantDirectories,
            issueParsers,
            project);
  }

  private ImmutableList<BlazeIssueParser.Parser> gazelleIssueParsers(
      Project project, WorkspaceRoot workspaceRoot) {
    return ImmutableList.<BlazeIssueParser.Parser>builder()
        .addAll(
            BlazeIssueParser.defaultIssueParsers(
                project, workspaceRoot, BlazeInvocationContext.ContextType.Sync))
        .addAll(GazelleIssueParsers.allGazelleIssueParsers(project))
        .build();
  }

  private void setUpUI(
      BlazeContext context,
      BlazeContext parentContext,
      Project project,
      ProgressIndicator indicator,
      ImmutableList<Parser> issueParsers) {
    // Do not make the entire sync fail if Gazelle fails.
    // It's possible that gazelle runs on directories that are included but irrelevant to the
    // current build.
    // Let the downstream sync machinery catch any build errors.
    context.setPropagatesErrors(false);
    ToolWindowScope parentToolWindowScope = parentContext.getScope(ToolWindowScope.class);
    Task parentToolWindowTask =
        parentToolWindowScope != null ? parentToolWindowScope.getTask() : null;
    Task gazelleTask = new Task(project, "Run Gazelle", Task.Type.SYNC, parentToolWindowTask);

    context
        .push(
            new ToolWindowScope.Builder(project, gazelleTask)
                .setProgressIndicator(indicator)
                .setIssueParsers(issueParsers)
                .setPopupBehavior(BlazeUserSettings.getInstance().getShowBlazeConsoleOnSync())
                .showSummaryOutput()
                .build())
        .push(new ProblemsViewScope(
            project, BlazeUserSettings.getInstance().getShowProblemsViewOnSync()))
        .push(
            new NotificationScope(
                project,
                "Gazelle",
                "Gazelle Run",
                "Gazelle completed successfully.",
                "Gazelle run completed with errors."))
        .push(new IdeaLogScope());
  }

  private ListenableFuture<Void> createGazelleFuture(
      Project project,
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      Label gazelleLabel) {
    return ProgressiveTaskWithProgressIndicator.builder(project, "Running Gazelle")
        .submitTask(
            indicator ->
                Scope.push(
                    parentContext,
                    context -> {
                      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

                      ImmutableList<BlazeIssueParser.Parser> issueParsers =
                          gazelleIssueParsers(project, workspaceRoot);
                      if (! this.runGazelleHeadless()) {
                        setUpUI(context, parentContext, project, indicator, issueParsers);
                      }

                      Collection<WorkspacePath> importantDirectories =
                          importantDirectories(project);
                      List<String> blazeFlags = blazeFlags(project, context, projectViewSet);
                      GazelleRunResult result =
                          doRunGazelle(
                              project,
                              context,
                              gazelleLabel,
                              workspaceRoot,
                              issueParsers,
                              importantDirectories,
                              blazeFlags);
                      if (result == GazelleRunResult.FAILED_TO_RUN) {
                        String error =
                            "Failed to invoke Gazelle. Please review that the Gazelle target can be"
                                + " built and run without errors.";
                        parentContext.output(
                            SummaryOutput.error(SummaryOutput.Prefix.TIMESTAMP, error));
                      } else if (result == GazelleRunResult.RAN_WITH_ERRORS) {
                        parentContext.output(
                            SummaryOutput.error(
                                SummaryOutput.Prefix.TIMESTAMP,
                                "Gazelle invocation finished with errors."));
                      } else {
                        parentContext.output(
                            SummaryOutput.output(
                                SummaryOutput.Prefix.TIMESTAMP, "Gazelle finished."));
                      }
                    }));
  }

  public void onSyncStart(Project project, BlazeContext parentContext, SyncMode syncMode)
      throws SyncScope.SyncFailedException, SyncScope.SyncCanceledException {
    if (syncMode == SyncMode.NO_BUILD || syncMode == SyncMode.STARTUP) {
      return;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    Optional<Label> gazelleBinary;
    try {
      gazelleBinary = this.getGazelleBinary(projectViewSet);
    } catch (InvalidTargetException e) {
      String msg = "Label for Gazelle target is invalid. Please re-check the plugin's settings.";
      parentContext.output(SummaryOutput.error(SummaryOutput.Prefix.TIMESTAMP, msg));
      // If there is an invalid target, the user wanted to have a gazelle.
      // Therefore, failing the sync is proper, as it will otherwise likely fail for out-of-date
      // changes.
      throw new SyncScope.SyncFailedException(msg, e);
    }

    gazelleBinary.ifPresent(
        label -> {
          ListenableFuture<Void> gazelleFuture =
              createGazelleFuture(project, parentContext, projectViewSet, label);
          FutureUtil.waitForFuture(parentContext, gazelleFuture)
              .withProgressMessage("Running Gazelle...")
              .timed("GazelleRun", TimingScope.EventType.BlazeInvocation)
              .onError("Gazelle failed")
              .run();
        });
  }

  private List<String> blazeFlags(
      Project project, BlazeContext context, ProjectViewSet projectViewSet) {
    return BlazeFlags.blazeFlags(
        project,
        projectViewSet,
        BlazeCommandName.BUILD,
        context,
        BlazeInvocationContext.SYNC_CONTEXT);
  }

  private Collection<WorkspacePath> importantDirectories(Project project) {
    // TODO: This currently fetches every directory in `directories` from the project view.
    //       For very large repositories, this can be quite slow.
    //       We should find a smarter way to figure out what to run gazelle on.
    ImportRoots roots = ImportRoots.forProjectSafe(project);
    Collection<WorkspacePath> paths =
        roots.rootDirectories().stream()
            .filter(dir -> !roots.excludeDirectories().contains(dir))
            .collect(Collectors.toSet());
    return paths;
  }
}
