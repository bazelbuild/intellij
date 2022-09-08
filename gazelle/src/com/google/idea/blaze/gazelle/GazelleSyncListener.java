package com.google.idea.blaze.gazelle;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncScope;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class GazelleSyncListener implements SyncListener {

    private Optional<Label> getGazelleBinary(ProjectViewSet projectViewSet) {
        Optional<Label> gazelleBinaryFromProjectSettings = projectViewSet.getScalarValue(GazelleSection.KEY);
        if (gazelleBinaryFromProjectSettings.isPresent()) {
            return gazelleBinaryFromProjectSettings;
        }
        GazelleUserSettings settings = GazelleUserSettings.getInstance();
        if (settings.getGazelleTarget().equals("")) {
            return Optional.empty();
        }
        return Optional.of(Label.create(settings.getGazelleTarget()));
    }
    public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode)
            throws SyncScope.SyncFailedException, SyncScope.SyncCanceledException {
        if (syncMode == SyncMode.NO_BUILD) {
            return;
        }
        ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
        Optional<Label> gazelleBinary = this.getGazelleBinary(projectViewSet);
        BuildSystem.BuildInvoker invoker = Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project, context);
        List<DirectoryEntry> importantDirectories = projectViewSet.listItems(DirectorySection.KEY);
        if (gazelleBinary.isPresent()) {
            BlazeImportSettings importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
            WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);

            ListenableFuture<String> blazeGazelleFuture = BlazeGazelleRunner.getInstance().runBlazeGazelle(
                    context,
                    invoker,
                    workspaceRoot,
                    ImmutableList.of(),
                    gazelleBinary.get(),
                    importantDirectories);
            FutureUtil.waitForFuture(context, blazeGazelleFuture)
                    .withProgressMessage("Running Gazelle...")
                    .timed("GazelleRun", TimingScope.EventType.BlazeInvocation)
                    .onError("Gazelle failed")
                    .run();
        }
    }
}
