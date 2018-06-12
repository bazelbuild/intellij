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
package com.google.idea.blaze.base.sync.actions;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.BuildTargetFinder;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.common.actionhelper.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Allows a partial sync of the project depending on what's been selected. */
public class PartialSyncAction extends BlazeProjectAction {

  private static class PartialSyncData {
    final String description;
    final ImmutableSet<TargetExpression> targets;

    PartialSyncData(String description, ImmutableSet<TargetExpression> targets) {
      this.description = description;
      this.targets = targets;
    }

    static PartialSyncData merge(PartialSyncData first, PartialSyncData second) {
      return new PartialSyncData(
          "Selected Files",
          ImmutableSet.<TargetExpression>builder()
              .addAll(first.targets)
              .addAll(second.targets)
              .build());
    }
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    PartialSyncData data = fromContext(project, e);
    if (data != null) {
      BlazeSyncManager.getInstance(project).partialSync(data.targets);
    }
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    PartialSyncData data = fromContext(project, e);
    ActionPresentationHelper.of(e)
        .disableIf(BlazeSyncStatus.getInstance(project).syncInProgress())
        .disableIf(data == null)
        .setText(data == null ? "Partially Sync File" : "Partially Sync " + data.description)
        .hideInContextMenuIfDisabled()
        .commit();
  }

  @Nullable
  private static PartialSyncData fromContext(Project project, AnActionEvent e) {
    PartialSyncData data = fromSelectedBuildTarget(e);
    return data != null ? data : fromSelectedFiles(project, e);
  }

  @Nullable
  private static PartialSyncData fromSelectedBuildTarget(AnActionEvent e) {
    PsiElement psi = e.getData(CommonDataKeys.PSI_ELEMENT);
    FuncallExpression target = PsiTreeUtil.getNonStrictParentOfType(psi, FuncallExpression.class);
    if (target == null) {
      return null;
    }
    Label label = target.resolveBuildLabel();
    return label != null
        ? new PartialSyncData(":" + label.targetName().toString(), ImmutableSet.of(label))
        : null;
  }

  @Nullable
  private static PartialSyncData fromSelectedFiles(Project project, AnActionEvent e) {
    VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (virtualFiles == null) {
      return null;
    }
    return Arrays.stream(virtualFiles)
        .filter(VirtualFile::isInLocalFileSystem)
        .map(vf -> fromFiles(project, vf))
        .filter(Objects::nonNull)
        .reduce(PartialSyncData::merge)
        .orElse(null);
  }

  @Nullable
  private static PartialSyncData fromFiles(Project project, VirtualFile vf) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    WorkspacePath path = root.workspacePathForSafe(new File(vf.getPath()));
    if (vf.isDirectory()) {
      return path == null
          ? null
          : new PartialSyncData(
              vf.getName() + "/...:all",
              ImmutableSet.of(TargetExpression.allFromPackageRecursive(path)));
    }
    if (isBuildFile(project, vf)) {
      return path == null || path.getParent() == null
          ? null
          : new PartialSyncData(
              vf.getParent().getName() + ":all",
              ImmutableSet.of(TargetExpression.allFromPackageNonRecursive(path.getParent())));
    }

    List<TargetExpression> targets =
        new ArrayList<>(
            SourceToTargetMap.getInstance(project)
                .getTargetsToBuildForSourceFile(new File(vf.getPath())));
    if (!targets.isEmpty()) {
      return new PartialSyncData(vf.getName(), ImmutableSet.copyOf(targets));
    }
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    if (importRoots == null) {
      return null;
    }
    BuildTargetFinder buildTargetFinder = new BuildTargetFinder(project, root, importRoots);
    TargetExpression target = buildTargetFinder.findTargetForFile(new File(vf.getPath()));
    return target == null ? null : new PartialSyncData(vf.getName(), ImmutableSet.of(target));
  }

  private static boolean isBuildFile(Project project, VirtualFile vf) {
    BuildSystemProvider provider =
        BuildSystemProvider.getBuildSystemProvider(Blaze.getBuildSystem(project));
    return provider != null && provider.isBuildFile(vf.getName());
  }
}
