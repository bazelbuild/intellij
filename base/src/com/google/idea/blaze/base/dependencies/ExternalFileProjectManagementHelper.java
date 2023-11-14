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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.dependencies.AddSourceToProjectHelper.LocationContext;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsConfigurable;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.UserBinaryFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Detects opened files which are in the workspace, but aren't in the project or libraries, and
 * offers to add them to the project (updating the project's 'targets' and 'directories' as
 * required).
 */
public class ExternalFileProjectManagementHelper implements EditorNotificationProvider {

  private static final BoolExperiment enabled =
      new BoolExperiment("project.external.source.management.enabled", true);

  private final Set<File> suppressedFiles = new HashSet<>();

  private static final ImmutableList<String> IGNORED_FILE_TYPE_NAMES =
      ImmutableList.of(
          "protobuf",
          "prototext",
          PlainTextFileType.INSTANCE.getName(),
          UnknownFileType.INSTANCE.getName(),
          BuildFileType.INSTANCE.getName());

  /** Whether the editor notification should be shown for this file type. */
  private static boolean supportedFileType(File file) {
    LanguageClass language =
        LanguageClass.fromExtension(FileUtilRt.getExtension(file.getName()).toLowerCase());
    if (language != null && !LanguageSupport.languagesSupportedByCurrentIde().contains(language)) {
      return false;
    }
    FileType type = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());

    if (IGNORED_FILE_TYPE_NAMES.contains(type.getName()) || type instanceof UserBinaryFileType) {
      return false;
    }
    return true;
  }

  @Override
  public @Nullable Function<? super FileEditor, EditorNotificationPanel> collectNotificationData(
      @NotNull Project project, @NotNull VirtualFile vf) {
    if (!enabled.getValue()) {
      return null;
    }
    if (!BlazeUserSettings.getInstance().getShowAddFileToProjectNotification()
        || suppressedFiles.contains(new File(vf.getPath()))) {
      return null;
    }
    File file = new File(vf.getPath());
    if (!supportedFileType(file)) {
      return null;
    }
    LocationContext context = AddSourceToProjectHelper.getContext(project, vf);
    if (context == null) {
      return null;
    }
    boolean inProjectDirectories = AddSourceToProjectHelper.sourceInProjectDirectories(context);
    boolean alreadyBuilt = AddSourceToProjectHelper.sourceCoveredByProjectViewTargets(context);
    if (alreadyBuilt && inProjectDirectories) {
      return null;
    }

    boolean addTargets = !alreadyBuilt && !AddSourceToProjectHelper.autoDeriveTargets(project);
    ListenableFuture<List<TargetInfo>> targetsFuture =
        addTargets
            ? AddSourceToProjectHelper.getTargetsBuildingSource(context)
            : Futures.immediateFuture(ImmutableList.of());
    if (targetsFuture == null) {
      return null;
    }

    return (editor) -> getPanel(project, vf, file, context, inProjectDirectories, targetsFuture);
  }

  @NotNull
  private EditorNotificationPanel getPanel(
      @NotNull Project project,
      @NotNull VirtualFile vf,
      File file,
      LocationContext context,
      boolean inProjectDirectories,
      ListenableFuture<List<TargetInfo>> targetsFuture) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setVisible(false); // starts off not visible until we get the query results
    panel.setText("Do you want to add this file to your project sources?");
    panel.createActionLabel(
        "Add file to project",
        () -> {
          AddSourceToProjectHelper.addSourceToProject(
              project, context.workspacePath, inProjectDirectories, targetsFuture);
          EditorNotifications.getInstance(project).updateNotifications(vf);
        });
    panel.createActionLabel(
        "Hide notification",
        () -> {
          // suppressed for this file until the editor is restarted
          suppressedFiles.add(file);
          EditorNotifications.getInstance(project).updateNotifications(vf);
        });
    panel.createActionLabel(
        "Don't show again",
        () -> {
          // disables the notification permanently, and focuses the relevant setting, so users know
          // how to turn it back on
          BlazeUserSettings.getInstance().setShowAddFileToProjectNotification(false);
          ShowSettingsUtilImpl.showSettingsDialog(
              project,
              BlazeUserSettingsCompositeConfigurable.ID,
              BlazeUserSettingsConfigurable.SHOW_ADD_FILE_TO_PROJECT.label());
        });

    targetsFuture.addListener(
        () -> {
          try {
            List<TargetInfo> targets = targetsFuture.get();
            if (!targets.isEmpty() || !inProjectDirectories) {
              panel.setVisible(true);
            }
          } catch (InterruptedException | ExecutionException e) {
            // ignore
          }
        },
        MoreExecutors.directExecutor());
    return panel;
  }

  static class UpdateNotificationsAfterSync implements SyncListener {

    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        ImmutableSet<Integer> buildIds,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      // update the editor notifications if the target map might have changed
      if (syncMode.involvesBlazeBuild() && syncResult.successful()) {
        EditorNotifications.getInstance(project).updateAllNotifications();
      }
    }
  }
}
