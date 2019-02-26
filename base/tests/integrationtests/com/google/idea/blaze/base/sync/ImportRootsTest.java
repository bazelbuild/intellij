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
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for ImportRoots */
@RunWith(JUnit4.class)
public class ImportRootsTest extends BlazeIntegrationTestCase {

  @Test
  public void testBazelArtifactDirectoriesAndProjectDataDirExcluded() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Bazel)
            .add(DirectoryEntry.include(new WorkspacePath("")))
            .build();

    ImmutableList<String> artifactDirs =
        ImmutableList.<String>builder()
            .addAll(
                BuildSystemProvider.getBuildSystemProvider(BuildSystem.Bazel)
                    .buildArtifactDirectories(workspaceRoot))
            .add(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY)
            .build();

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
            .add(DirectoryEntry.include(new WorkspacePath("foo/bar")))
            .build();

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("foo/bar"));
    assertThat(importRoots.excludeDirectories()).isEmpty();
  }

  @Test
  public void testNoAddedExclusionsForBlaze() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("")))
            .build();

    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath(""));
    assertThat(importRoots.excludeDirectories()).isEmpty();
  }

  // if the workspace root is an included directory, all rules should be imported as sources.
  @Test
  public void testAllLabelsIncludedUnderWorkspaceRoot() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("")))
            .build();

    assertThat(importRoots.importAsSource(Label.create("//:target"))).isTrue();
    assertThat(importRoots.importAsSource(Label.create("//foo/bar:target"))).isTrue();
  }

  @Test
  public void testExternalWorkspaceLabelsNotIncludedUnderWorkspaceRoot() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("")))
            .build();

    assertThat(importRoots.importAsSource(Label.create("@lib//:target"))).isFalse();
  }

  @Test
  public void testNonOverlappingDirectoriesAreNotFilteredOut() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root0/subdir0")))
            .add(DirectoryEntry.include(new WorkspacePath("root0/subdir1")))
            .add(DirectoryEntry.include(new WorkspacePath("root1")))
            .build();
    assertThat(importRoots.rootDirectories())
        .containsExactly(
            new WorkspacePath("root0/subdir0"),
            new WorkspacePath("root0/subdir1"),
            new WorkspacePath("root1"));
  }

  @Test
  public void testProjectTargetsAreTreatedAsSource() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(
                ProjectViewSet.builder()
                    .add(
                        ProjectView.builder()
                            .add(
                                ListSection.builder(TargetSection.KEY)
                                    .add(TargetExpression.fromStringSafe("//foo:all"))
                                    .add(TargetExpression.fromStringSafe("//bar/..."))
                                    .add(TargetExpression.fromStringSafe("//baz:target")))
                            .build())
                    .build())
            .build();
    assertThat(importRoots.importAsSource(Label.create("//foo:some-target"))).isTrue();
    assertThat(importRoots.importAsSource(Label.create("//bar/subpackage:target"))).isTrue();
    assertThat(importRoots.importAsSource(Label.create("//baz:target"))).isTrue();
    assertThat(importRoots.importAsSource(Label.create("//baz:other-target"))).isFalse();
    assertThat(importRoots.importAsSource(Label.create("//dir:target"))).isFalse();
  }

  @Test
  public void testOverlappingDirectoriesAreFilteredOut() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .add(DirectoryEntry.include(new WorkspacePath("root")))
            .add(DirectoryEntry.include(new WorkspacePath("root/subdir")))
            .build();
    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("root"));
  }

  @Test
  public void testWorkspaceRootIsOnlyDirectoryLeft() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.include(new WorkspacePath(".")))
            .add(DirectoryEntry.include(new WorkspacePath(".")))
            .add(DirectoryEntry.include(new WorkspacePath("root/subdir")))
            .build();
    assertThat(importRoots.rootDirectories()).containsExactly(new WorkspacePath("."));
  }

  @Test
  public void testOverlappingExcludesAreFiltered() {
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, BuildSystem.Blaze)
            .add(DirectoryEntry.exclude(new WorkspacePath("root")))
            .add(DirectoryEntry.exclude(new WorkspacePath("root")))
            .add(DirectoryEntry.exclude(new WorkspacePath("root/subdir")))
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
