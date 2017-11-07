/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.sharding.WildcardTargetPattern;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverProvider;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.UserBinaryFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Consumer;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Detects opened files which are in the workspace, but aren't in the project or libraries, and
 * offers to add them to the project (updating the project's 'targets' and 'directories' as
 * required).
 */
public class ExternalFileProjectManagementHelper
    extends EditorNotifications.Provider<EditorNotificationPanel> {

  private static final BoolExperiment enabled =
      new BoolExperiment("project.external.source.management.enabled", true);

  private static final Key<EditorNotificationPanel> KEY = Key.create("add source to project");

  private static final ImmutableList<Class<? extends FileType>> IGNORED_FILE_TYPES =
      ImmutableList.of(
          PlainTextFileType.class,
          UnknownFileType.class,
          BuildFileType.class,
          UserBinaryFileType.class);

  private final Project project;

  public ExternalFileProjectManagementHelper(Project project) {
    this.project = project;
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  /** Whether the editor notification should be shown for this file type. */
  private static boolean supportedFileType(File file) {
    LanguageClass languageClass =
        LanguageClass.fromExtension(FileUtilRt.getExtension(file.getName()).toLowerCase());
    if (languageClass != null && !supportedLanguage(languageClass)) {
      return false;
    }
    FileType type = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());

    for (Class<? extends FileType> clazz : IGNORED_FILE_TYPES) {
      if (clazz.isInstance(type)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(VirtualFile vf, FileEditor fileEditor) {
    if (!enabled.getValue() || !SourceToTargetProvider.hasProvider()) {
      return null;
    }
    BlazeProjectData syncData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (syncData == null) {
      return null;
    }
    File file = new File(vf.getPath());
    if (!supportedFileType(file)) {
      return null;
    }

    WorkspacePath workspacePath = getWorkspacePath(file);
    if (workspacePath == null) {
      return null;
    }
    WorkspacePath parent = workspacePath.getParent();
    if (parent == null) {
      return null;
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return null;
    }
    boolean inProjectDirectories =
        sourceInProjectDirectories(project, projectViewSet, workspacePath);

    if (inProjectDirectories
        && !SourceToTargetMap.getInstance(project).getRulesForSourceFile(file).isEmpty()) {
      // early-out if source covered by previously built targets *and* in the source directories
      return null;
    }
    // early-out if source is trivially covered by project targets (e.g. because there's a wildcard
    // target pattern for the parent package)
    List<TargetExpression> projectTargets = projectViewSet.listItems(TargetSection.KEY);
    WorkspacePath blazePackage = findBlazePackagePath(project, parent);
    if (inProjectDirectories
        && (blazePackage == null
            || packageCoveredByWildcardPattern(projectTargets, blazePackage))) {
      return null;
    }
    // Finally, query the exact targets building this source file.
    // This is required to handle project targets which failed to build
    ListenableFuture<List<TargetInfo>> targetsFuture =
        BlazeExecutor.getInstance()
            .submit(
                () -> {
                  List<TargetInfo> result =
                      SourceToTargetProvider.findTargetsBuildingSourceFile(
                          project, workspacePath.relativePath());
                  return filterTargets(syncData.targetMap, projectTargets, result);
                });

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setVisible(false); // starts off not visible until we get the query results
    panel.setText("Do you want to add this file to your project sources?");
    panel.createActionLabel(
        "Add file to project",
        () -> {
          addSourceToProject(project, workspacePath, inProjectDirectories, targetsFuture);
          EditorNotifications.getInstance(project).updateNotifications(vf);
        });
    // TODO(brendandouglas): Add 'help' and/or 'suppress notification' actions
    // panel.createActionLabel("Don't show again", () -> suppressNotification());

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

  /**
   * Returns the list of targets not already in the project, which aren't known to be of an
   * unsupported language.
   */
  private List<TargetInfo> filterTargets(
      TargetMap targetMap, List<TargetExpression> projectViewTargets, List<TargetInfo> targets) {
    if (sourceInProjectTargets(targetMap, projectViewTargets, targets)) {
      return ImmutableList.of();
    }
    targets.removeIf(t -> !supportedTargetKind(t));
    return targets;
  }

  private static boolean supportedTargetKind(TargetInfo target) {
    Kind kind = Kind.fromString(target.kind);
    return kind == null || supportedLanguage(kind.languageClass);
  }

  private static boolean supportedLanguage(LanguageClass language) {
    return LanguageSupport.languagesSupportedByCurrentIde().contains(language);
  }

  private static boolean sourceInProjectDirectories(
      Project project, ProjectViewSet projectViewSet, WorkspacePath source) {
    ImportRoots importRoots =
        ImportRoots.builder(WorkspaceRoot.fromProject(project), Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    return importRoots.containsWorkspacePath(source);
  }

  /**
   * Returns true if the project targets (both included and excluded targets) contain one of the
   * targets building the source file.
   */
  private boolean sourceInProjectTargets(
      TargetMap targetMap,
      List<TargetExpression> projectViewTargets,
      List<TargetInfo> targetsBuildingSource) {
    // treat excluded and included project targets identically
    projectViewTargets =
        projectViewTargets
            .stream()
            .map(ExternalFileProjectManagementHelper::unexclude)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    for (TargetInfo targetInfo : targetsBuildingSource) {
      TargetExpression target = TargetExpression.fromStringSafe(targetInfo.name);
      if (target == null) {
        continue;
      }
      if (projectViewTargets.contains(target)
          || (target instanceof Label
              && targetMap.contains(TargetKey.forPlainTarget((Label) target)))) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static TargetExpression unexclude(TargetExpression target) {
    return target.isExcluded()
        ? TargetExpression.fromStringSafe(target.toString().substring(1))
        : target;
  }

  private static boolean packageCoveredByWildcardPattern(
      List<TargetExpression> projectTargets, WorkspacePath blazePackage) {
    return projectTargets
        .stream()
        .map(WildcardTargetPattern::fromExpression)
        .anyMatch(wildcard -> wildcard != null && wildcard.coversPackage(blazePackage));
  }

  @Nullable
  private static WorkspacePath findBlazePackagePath(Project project, WorkspacePath source) {
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return null;
    }
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    while (source != null) {
      if (provider.findBuildFileInDirectory(pathResolver.resolveToFile(source)) != null) {
        return source;
      }
      source = source.getParent();
    }
    return null;
  }

  /**
   * Given the workspace targets building a source file, updates the .blazeproject 'directories' and
   * 'targets' sections accordingly.
   */
  private static void addSourceToProject(
      Project project,
      WorkspacePath workspacePath,
      boolean inProjectDirectories,
      Future<List<TargetInfo>> targetsFuture) {
    List<TargetInfo> targets;
    try {
      targets = targetsFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      return;
    }
    boolean addDirectory = !inProjectDirectories;
    boolean addTarget = !targets.isEmpty();
    if (!addDirectory && !addTarget) {
      return;
    }
    if (targets.size() <= 1) {
      AddSourceToProjectHelper.addSourceAndTargetsToProject(
          project, workspacePath, convertTargets(targets));
      return;
    }
    AddSourceToProjectDialog dialog = new AddSourceToProjectDialog(project, targets);
    dialog
        .showAndGetOk()
        .doWhenDone(
            (Consumer<Boolean>)
                ok -> {
                  if (ok) {
                    AddSourceToProjectHelper.addSourceAndTargetsToProject(
                        project, workspacePath, convertTargets(dialog.getSelectedTargets()));
                  }
                });
  }

  private static List<TargetExpression> convertTargets(List<TargetInfo> targets) {
    return targets
        .stream()
        .map(t -> TargetExpression.fromStringSafe(t.name))
        .collect(Collectors.toList());
  }

  @Nullable
  private WorkspacePath getWorkspacePath(File file) {
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return null;
    }
    return pathResolver.getWorkspacePath(file);
  }
}
