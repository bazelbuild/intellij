/*
 * Copyright 2022 The Bazel Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.qsync.QuerySyncManager.OperationType;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.DepsBuildType;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides a custom notification at the top of file editors to build dependencies and enable analysis.
 */
public class QuerySyncNotificationProvider implements EditorNotificationProvider, DumbAware {

    @Override
    @Nullable
    public Function<? super FileEditor, ? extends JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
        return fileEditor -> {
            if (Blaze.getProjectType(project) != ProjectType.QUERY_SYNC) {
                return null;
            }

            if (!(fileEditor instanceof TextEditor)) {
                return null;
            }
            Optional<OperationType> currentOperation = QuerySyncManager.getInstance(project).currentOperation();
            if (currentOperation.isPresent()) {
                var notificationText =
                        currentOperation.get() == OperationType.SYNC
                                ? "Syncing project..."
                                : "Building dependencies...";
                return new QuerySyncNotificationPanel(fileEditor, project, notificationText, "");
            }

            BuildDependenciesHelper buildDepsHelper = new BuildDependenciesHelper(project, DepsBuildType.SELF);
            TargetsToBuild toBuild = buildDepsHelper.getTargetsToEnableAnalysisFor(file);

            if (toBuild.isEmpty()) {
                return null;
            }
            if (toBuild.type() != TargetsToBuild.Type.SOURCE_FILE) {
                return null;
            }

            int missing = buildDepsHelper.getSourceFileMissingDepsCount(toBuild);
            String notificationText;
            String actionText;
            if (missing > 0) {
                String dependency = StringUtil.pluralize("dependency", missing);
                notificationText = String.format("Analysis disabled - missing %d %s ", missing, dependency);
                actionText = QuerySync.BUILD_DEPENDENCIES_ACTION_NAME;
            } else {
                notificationText = "Query Sync";
                actionText = "Rebuild dependencies";
            }
            return null;
        };
    }

    private class QuerySyncNotificationPanel extends EditorNotificationPanel {
        QuerySyncNotificationPanel(FileEditor editor, Project project, String notificationText, String actionText) {
            super(editor, Status.Info);
            setText(notificationText);

            String actionId = "Blaze.BuildDependencies";
            var actionLabel = createActionLabel(actionText, () -> executeAction(actionId));
            actionLabel.addHyperlinkListener(event -> EditorNotifications.getInstance(project).updateAllNotifications());

            var tooltip = actionLabel.createToolTip();
            tooltip.setTipText("Builds the external dependencies needed for this file and enables analysis");
        }
    }
}
