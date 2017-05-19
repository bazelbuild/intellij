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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ImportRoots */
@RunWith(JUnit4.class)
public class ImportRootsTest extends BlazeIntegrationTestCase {

  @Test
  public void testBazelArtifactDirectoriesExcluded() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Bazel)
            .add(new DirectoryEntry(new WorkspacePath(""), true))
            .build();

    ImmutableList<String> artifactDirs =
        BuildSystemProvider.getBuildSystemProvider(BuildSystem.Bazel)
            .buildArtifactDirectories(workspaceRoot);

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath(""));
    assertThat(
            importRoots
                .excludeDirectories()
                .stream()
                .map(WorkspacePath::relativePath)
                .collect(Collectors.toList()))
        .containsExactlyElementsIn(artifactDirs);

    assertThat(artifactDirs).contains("bazel-" + workspaceRoot.directory().getName());
  }

  @Test
  public void testNoAddedExclusionsWithoutWorkspaceRootInclusion() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Bazel)
            .add(new DirectoryEntry(new WorkspacePath("foo/bar"), true))
            .build();

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("foo/bar"));
    assertThat(importRoots.excludeDirectories()).isEmpty();
  }

  @Test
  public void testNoAddedExclusionsForBlaze() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath(""), true))
            .build();

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath(""));
    assertThat(importRoots.excludeDirectories()).isEmpty();
  }

  // if the workspace root is an included directory, all rules should be imported as sources.
  @Test
  public void testAllLabelsIncludedUnderWorkspaceRoot() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath(""), true))
            .build();

    assertThat(importRoots.importAsSource(Label.create("//:target"))).isTrue();
    assertThat(importRoots.importAsSource(Label.create("//foo/bar:target"))).isTrue();
  }

  @Test
  public void testExternalWorkspaceLabelsNotIncludedUnderWorkspaceRoot() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath(""), true))
            .build();

    assertThat(importRoots.importAsSource(Label.create("@lib//:target"))).isFalse();
  }

  @Test
  public void testNonOverlappingDirectoriesAreNotFilteredOut() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath("root0/subdir0"), true))
            .add(new DirectoryEntry(new WorkspacePath("root0/subdir1"), true))
            .add(new DirectoryEntry(new WorkspacePath("root1"), true))
            .build();
    assertThat(importRoots.rootDirectories())
        .containsExactly(
            new WorkspacePath("root0/subdir0"),
            new WorkspacePath("root0/subdir1"),
            new WorkspacePath("root1"));
  }

  @Test
  public void testOverlappingDirectoriesAreFilteredOut() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath("root"), true))
            .add(new DirectoryEntry(new WorkspacePath("root"), true))
            .add(new DirectoryEntry(new WorkspacePath("root/subdir"), true))
            .build();
    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("root"));
  }

  @Test
  public void testWorkspaceRootIsOnlyDirectoryLeft() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath("."), true))
            .add(new DirectoryEntry(new WorkspacePath("."), true))
            .add(new DirectoryEntry(new WorkspacePath("root/subdir"), true))
            .build();
    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("."));
  }

  @Test
  public void testOverlappingExcludesAreFiltered() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(new DirectoryEntry(new WorkspacePath("root"), false))
            .add(new DirectoryEntry(new WorkspacePath("root"), false))
            .add(new DirectoryEntry(new WorkspacePath("root/subdir"), false))
            .build();
    assertThat(importRoots.excludeDirectories()).containsExactly(new WorkspacePath("root"));
  }

  @Test
  public void testContainsWorkspacePath_samePath() throws Exception {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .build();

    assertThat(importRoots.containsWorkspacePath(new WorkspacePath("root"))).isTrue();
  }

  @Test
  public void testContainsWorkspacePath_subdirectory() throws Exception {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .build();

    assertThat(importRoots.containsWorkspacePath(new WorkspacePath("root/subdir"))).isTrue();
  }

  @Test
  public void testContainsWorkspacePath_differentRoot() throws Exception {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .build();

    assertThat(importRoots.containsWorkspacePath(new WorkspacePath("otherroot"))).isFalse();
  }

  @Test
  public void testContainsWorkspacePath_similarRoot() throws Exception {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .build();

    assertThat(importRoots.containsWorkspacePath(new WorkspacePath("root2/subdir"))).isFalse();
  }

  @Test
  public void testContainsWorkspacePath_excludedParentsAreHandled() throws Exception {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .add(DirectoryEntry.exclude(new WorkspacePath("root/a")))
            .build();

    assertThat(importRoots.containsWorkspacePath(new WorkspacePath("root/a/b"))).isFalse();
  }
}
