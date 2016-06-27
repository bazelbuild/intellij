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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;

import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for ImportRoots
 */
public class ImportRootsTest extends BlazeIntegrationTestCase {

  public void testBazelArtifactDirectoriesExcluded() {
    ImportRoots importRoots = ImportRoots.builder(workspaceRoot, BuildSystem.Bazel)
      .add(new DirectoryEntry(new WorkspacePath(""), true))
      .build();

    ImmutableList<String> artifactDirs = BuildSystemProvider.getBuildSystemProvider(BuildSystem.Bazel)
      .buildArtifactDirectories(workspaceRoot);

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath(""));
    assertThat(
      importRoots.excludeDirectories()
        .stream()
        .map(WorkspacePath::relativePath)
        .collect(Collectors.toList())
    ).containsExactlyElementsIn(artifactDirs);

    assertThat(artifactDirs).contains("bazel-" + workspaceRoot.directory().getName());
  }

  public void testNoAddedExclusionsWithoutWorkspaceRootInclusion() {
    ImportRoots importRoots = ImportRoots.builder(workspaceRoot, BuildSystem.Bazel)
      .add(new DirectoryEntry(new WorkspacePath("foo/bar"), true))
      .build();

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("foo/bar"));
    assertThat(importRoots.excludeDirectories()).isEmpty();
  }

  public void testNoAddedExclusionsForBlaze() {
    ImportRoots importRoots = ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
      .add(new DirectoryEntry(new WorkspacePath(""), true))
      .build();

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath(""));
    assertThat(importRoots.excludeDirectories()).isEmpty();
  }

  // if the workspace root is an included directory, all rules should be imported as sources.
  public void testAllLabelsIncludedUnderWorkspaceRoot() {
    ImportRoots importRoots = ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
      .add(new DirectoryEntry(new WorkspacePath(""), true))
      .build();

    assertThat(importRoots.importAsSource(new Label("//:target"))).isTrue();
    assertThat(importRoots.importAsSource(new Label("//foo/bar:target"))).isTrue();
  }

}
