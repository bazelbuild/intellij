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
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.util.io.FileUtil;
import java.util.Collection;
import java.util.Set;

/** The roots to import. Derived from project view. */
public final class ImportRoots {
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

      // Remove any duplicates and any overlapping directories
      ImmutableSet.Builder<WorkspacePath> minimalRootDirectories = ImmutableSet.builder();
      for (WorkspacePath directory : rootDirectories) {
        boolean ok = true;
        for (WorkspacePath otherDirectory : rootDirectories) {
          if (directory == otherDirectory) {
            continue;
          }
          ok = ok && !isAncestor(otherDirectory.relativePath(), directory.relativePath());
        }
        if (ok) {
          minimalRootDirectories.add(directory);
        }
      }

      // for bazel projects, if we're including the workspace root,
      // we force-exclude the bazel artifact directories
      // (e.g. bazel-bin, bazel-genfiles).
      if (buildSystem == BuildSystem.Bazel && hasWorkspaceRoot(rootDirectories)) {
        excludeBuildSystemArtifacts();
      }
      return new ImportRoots(minimalRootDirectories.build(), excludeDirectoriesBuilder.build());
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
    boolean included = false;
    boolean excluded = false;
    for (WorkspacePath workspacePath : rootDirectories()) {
      included = included || matchesLabel(workspacePath, label);
    }
    for (WorkspacePath workspacePath : excludeDirectories()) {
      excluded = excluded || matchesLabel(workspacePath, label);
    }
    return included && !excluded;
  }

  private static boolean matchesLabel(WorkspacePath workspacePath, Label label) {
    if (workspacePath.isWorkspaceRoot()) {
      return true;
    }
    String moduleLabelStr = label.toString();
    int packagePrefixLength = "//".length();
    int nextCharIndex = workspacePath.relativePath().length() + packagePrefixLength;
    if (moduleLabelStr.startsWith(workspacePath.relativePath(), packagePrefixLength)
        && moduleLabelStr.length() >= nextCharIndex) {
      char c = moduleLabelStr.charAt(nextCharIndex);
      return c == '/' || c == ':';
    }
    return false;
  }

  /** Returns true if 'path' is a strict child of 'ancestorPath'. */
  private static boolean isAncestor(String ancestorPath, String path) {
    // FileUtil.isAncestor has a bug in its handling of equal,
    // empty paths (it ignores the 'strict' flag in this case).
    if (ancestorPath.equals(path)) {
      return false;
    }
    return FileUtil.isAncestor(ancestorPath, path, true);
  }
}
