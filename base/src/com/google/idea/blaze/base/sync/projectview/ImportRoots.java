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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.util.WorkspacePathUtil;
import com.intellij.openapi.project.Project;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** The roots to import. Derived from project view. */
public final class ImportRoots {

  /** Returns the ImportRoots for the project, or null if it's not a blaze project. */
  @Nullable
  public static ImportRoots forProjectSafe(Project project) {
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (root == null || projectViewSet == null) {
      return null;
    }
    return ImportRoots.builder(root, Blaze.getBuildSystem(project)).add(projectViewSet).build();
  }

  /** Builder for import roots */
  public static class Builder {
    private final ImmutableCollection.Builder<WorkspacePath> rootDirectoriesBuilder =
        ImmutableList.builder();
    private final ImmutableSet.Builder<WorkspacePath> excludeDirectoriesBuilder =
        ImmutableSet.builder();

    private final WorkspaceRoot workspaceRoot;
    private final BuildSystem buildSystem;

    private Builder(WorkspaceRoot workspaceRoot, BuildSystem buildSystem) {
      this.workspaceRoot = workspaceRoot;
      this.buildSystem = buildSystem;
    }

    public Builder add(ProjectViewSet projectViewSet) {
      for (DirectoryEntry entry : projectViewSet.listItems(DirectorySection.KEY)) {
        add(entry);
      }
      return this;
    }

    @VisibleForTesting
    public Builder add(DirectoryEntry entry) {
      if (entry.included) {
        rootDirectoriesBuilder.add(entry.directory);
      } else {
        excludeDirectoriesBuilder.add(entry.directory);
      }
      return this;
    }

    public ImportRoots build() {
      ImmutableCollection<WorkspacePath> rootDirectories = rootDirectoriesBuilder.build();
      // for bazel projects, if we're including the workspace root,
      // we force-exclude the bazel artifact directories
      // (e.g. bazel-bin, bazel-genfiles).
      if (buildSystem == BuildSystem.Bazel && hasWorkspaceRoot(rootDirectories)) {
        excludeBuildSystemArtifacts();
      }
      ImmutableSet<WorkspacePath> minimalExcludes =
          WorkspacePathUtil.calculateMinimalWorkspacePaths(excludeDirectoriesBuilder.build());

      // Remove any duplicates, overlapping, or excluded directories
      ImmutableSet<WorkspacePath> minimalRootDirectories =
          WorkspacePathUtil.calculateMinimalWorkspacePaths(rootDirectories, minimalExcludes);

      return new ImportRoots(minimalRootDirectories, minimalExcludes);
    }

    private void excludeBuildSystemArtifacts() {
      for (String dir :
          BuildSystemProvider.getBuildSystemProvider(buildSystem)
              .buildArtifactDirectories(workspaceRoot)) {
        excludeDirectoriesBuilder.add(new WorkspacePath(dir));
      }
    }

    private static boolean hasWorkspaceRoot(ImmutableCollection<WorkspacePath> rootDirectories) {
      return rootDirectories.stream().anyMatch(WorkspacePath::isWorkspaceRoot);
    }
  }

  private final ImmutableCollection<WorkspacePath> rootDirectories;
  private final ImmutableSet<WorkspacePath> excludeDirectories;

  public static Builder builder(WorkspaceRoot workspaceRoot, BuildSystem buildSystem) {
    return new Builder(workspaceRoot, buildSystem);
  }

  private ImportRoots(
      ImmutableCollection<WorkspacePath> rootDirectories,
      ImmutableSet<WorkspacePath> excludeDirectories) {
    this.rootDirectories = rootDirectories;
    this.excludeDirectories = excludeDirectories;
  }

  public Collection<WorkspacePath> rootDirectories() {
    return rootDirectories;
  }

  public Set<WorkspacePath> excludeDirectories() {
    return excludeDirectories;
  }

  /** Returns true if this rule should be imported as source. */
  public boolean importAsSource(Label label) {
    return containsLabel(label);
  }

  private boolean containsLabel(Label label) {
    return !label.isExternal() && containsWorkspacePath(label.blazePackage());
  }

  public boolean containsWorkspacePath(WorkspacePath workspacePath) {
    boolean included = false;
    boolean excluded = false;
    for (WorkspacePath rootDirectory : rootDirectories()) {
      included = included || isSubdirectory(rootDirectory, workspacePath);
    }
    for (WorkspacePath excludeDirectory : excludeDirectories()) {
      excluded = excluded || isSubdirectory(excludeDirectory, workspacePath);
    }
    return included && !excluded;
  }

  private static boolean isSubdirectory(WorkspacePath ancestor, WorkspacePath descendant) {
    if (ancestor.isWorkspaceRoot()) {
      return true;
    }
    Path ancestorPath = FileSystems.getDefault().getPath(ancestor.relativePath());
    Path descendantPath = FileSystems.getDefault().getPath(descendant.relativePath());
    return descendantPath.startsWith(ancestorPath);
  }
}
