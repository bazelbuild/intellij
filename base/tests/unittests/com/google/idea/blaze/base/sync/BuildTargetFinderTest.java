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
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.ExtensionPoint;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for build target finder. */
@RunWith(JUnit4.class)
public class BuildTargetFinderTest extends BlazeTestCase {

  private static class MockFileAttributeProvider extends FileAttributeProvider {
    final Set<File> files = Sets.newHashSet();

    void addFiles(File... files) {
      this.files.addAll(Lists.newArrayList(files));
    }

    @Override
    public boolean isFile(File file) {
      return files.contains(file);
    }

    @Override
    public boolean exists(File file) {
      return files.contains(file);
    }
  }

  private final MockFileAttributeProvider fileAttributeProvider = new MockFileAttributeProvider();
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/root"));

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(FileAttributeProvider.class, fileAttributeProvider);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(
        BlazeImportSettingsManager.class, mock(BlazeImportSettingsManager.class));
    ExtensionPoint<BuildSystemProvider> extensionPoint =
        registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class);
    extensionPoint.registerExtension(new BazelBuildSystemProvider());
  }

  private BuildTargetFinder buildTargetFinder(Collection<WorkspacePath> roots) {
    ImportRoots.Builder builder = ImportRoots.builder(workspaceRoot, BuildSystem.Bazel);
    for (WorkspacePath root : roots) {
      builder.add(DirectoryEntry.include(root));
    }

    return new BuildTargetFinder(project, workspaceRoot, builder.build());
  }

  @Test
  public void firstBuildFileInDirectoryIsFound() {
    BuildTargetFinder buildTargetFinder =
        buildTargetFinder(ImmutableList.of(new WorkspacePath(".")));

    fileAttributeProvider.addFiles(
        new File("/root/j/c/g/some/BUILD"),
        new File("/root/j/c/g/BUILD"),
        new File("/root/j/c/g/some/dir/File.java"));

    assertThat(buildTargetFinder.findTargetForFile(new File("/root/j/c/g/some/dir/File.java")))
        .isEqualTo(TargetExpression.fromStringSafe("//j/c/g/some:all"));
  }

  @Test
  public void testNothingFound() {
    BuildTargetFinder buildTargetFinder =
        buildTargetFinder(ImmutableList.of(new WorkspacePath(".")));

    fileAttributeProvider.addFiles(
        new File("/root/j/c/g/other/BUILD"), new File("/root/j/c/g/some/dir/File.java"));

    assertThat(buildTargetFinder.findTargetForFile(new File("/root/j/c/g/some/dir/File.java")))
        .isNull();
  }

  @Test
  public void buildFilesBelowWorkspaceRootsNotFound() {
    BuildTargetFinder buildTargetFinder =
        buildTargetFinder(ImmutableList.of(new WorkspacePath("j/c/g/some")));

    fileAttributeProvider.addFiles(
        new File("/root/j/c/g/BUILD"), new File("/root/j/c/g/some/dir/File.java"));

    assertThat(buildTargetFinder.findTargetForFile(new File("/root/j/c/g/some/dir/File.java")))
        .isNull();
  }

  @Test
  public void rightContentRootFound() {
    BuildTargetFinder buildTargetFinder =
        buildTargetFinder(
            ImmutableList.of(
                new WorkspacePath("j/c/g/foo"),
                new WorkspacePath("j/c/g/bar"),
                new WorkspacePath("j/c/g/baz")));

    fileAttributeProvider.addFiles(
        new File("/root/j/c/g/BUILD"),
        new File("/root/j/c/g/foo/BUILD"),
        new File("/root/j/c/g/bar/BUILD"),
        new File("/root/j/c/g/baz/BUILD"),
        new File("/root/j/c/g/bar/dir/File.java"));

    assertThat(buildTargetFinder.findTargetForFile(new File("/root/j/c/g/bar/dir/File.java")))
        .isEqualTo(TargetExpression.fromStringSafe("//j/c/g/bar:all"));
  }

  @Test
  public void buildFileFindsSelf() {
    BuildTargetFinder buildTargetFinder =
        buildTargetFinder(ImmutableList.of(new WorkspacePath(".")));

    fileAttributeProvider.addFiles(
        new File("/root/j/c/g/some/BUILD"), new File("/root/j/c/g/BUILD"));

    assertThat(buildTargetFinder.findTargetForFile(new File("/root/j/c/g/some/BUILD")))
        .isEqualTo(TargetExpression.fromStringSafe("//j/c/g/some:all"));
  }
}
