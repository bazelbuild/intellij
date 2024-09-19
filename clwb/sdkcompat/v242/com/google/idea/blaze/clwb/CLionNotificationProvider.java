/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.wizard2.BazelImportCurrentProjectAction;
import com.google.idea.blaze.base.wizard2.BazelNotificationProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationProvider;
import com.jetbrains.cidr.lang.daemon.OCFileScopeProvider;
import com.jetbrains.cidr.project.ui.ProjectStatusHelperKt;
import com.jetbrains.cidr.project.ui.notifications.EditorNotificationWarningProvider;
import com.jetbrains.cidr.project.ui.notifications.ProjectNotification;
import com.jetbrains.cidr.project.ui.popup.ProjectFixesProvider;
import com.jetbrains.cidr.project.ui.widget.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.cidr.project.ui.ProjectStatusHelperKt.isProjectAwareFile;

// #api241
public class CLionNotificationProvider implements ProjectFixesProvider, WidgetStatusProvider,
    EditorNotificationWarningProvider {

  private static Boolean registered = false;

  private static void unregisterGenericProvider(Project project) {
    final var extensionPoint = EditorNotificationProvider.EP_NAME.getPoint(project);

    // Note: We need to remove the default style of showing project status and fixes used in
    // Android Studio and IDEA to introduce CLion's PSW style.
    for (final var extension : extensionPoint.getExtensions()) {
      if (extension instanceof BazelNotificationProvider) {
        extensionPoint.unregisterExtension(extension);
      }
    }
  }

  private static void registerSpecificProvider() {
    final var projectFixes = ProjectFixesProvider.Companion.getEP_NAME().getPoint();
    projectFixes.registerExtension(new CLionNotificationProvider());

    final var projectNotifications = EditorNotificationWarningProvider.Companion.getEP_NAME().getPoint();
    projectNotifications.registerExtension(new CLionNotificationProvider());

    final var widgetStatus = WidgetStatusProvider.Companion.getEP_NAME().getPoint();
    widgetStatus.registerExtension(new CLionNotificationProvider());
  }

  public static void register(Project project) {
    unregisterGenericProvider(project);

    if (!registered) {
      registerSpecificProvider();
    }
    registered = true;
  }

  private static Boolean isBazelAwareFile(Project project, VirtualFile file) {
    if (Blaze.isBlazeProject(project)) {
      return false;
    }

    if (!isProjectAwareFile(file, project) && file.getFileType() != BuildFileType.INSTANCE) {
      return false;
    }

    if (OCFileScopeProvider.Companion.getProjectSourceLocationKind(project, file).isInProject()) {
      return false;
    }

    if (!BazelImportCurrentProjectAction.projectCouldBeImported(project)) {
      return false;
    }

    if (project.getBasePath() == null) {
      return false;
    }

    return true;
  }

  @NotNull
  @Override
  public List<AnAction> collectFixes(@NotNull Project project, @Nullable VirtualFile file, @NotNull DataContext dataContext) {
    if (file == null || !isBazelAwareFile(project, file)) {
      return List.of();
    }

    final var root = project.getBasePath();
    if (root == null) {
      return List.of();
    }

    return List.of(new ImportBazelAction(root));
  }

  private static class ImportBazelAction extends AnAction {

    private final String root;

    public ImportBazelAction(String root) {
      super("Import Bazel project");
      this.root = root;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
      BazelImportCurrentProjectAction.createAction(root).run();
    }
  }

  @Nullable
  @Override
  public ProjectNotification getProjectNotification(@NotNull Project project,
      @NotNull VirtualFile virtualFile) {
    return ProjectStatusHelperKt.convertStatus(getWidgetStatus(project, virtualFile));
  }

  @Nullable
  @Override
  public WidgetStatus getWidgetStatus(@NotNull Project project, @Nullable VirtualFile file) {
    if (Blaze.isBlazeProject(project)) {
      return new DefaultWidgetStatus(Status.OK, Scope.Project, "Project is configured");
    }

    if (file == null || !isBazelAwareFile(project, file)) {
      return null;
    }

    return new DefaultWidgetStatus(Status.Warning, Scope.Project, "Project is not configured");
  }
}
