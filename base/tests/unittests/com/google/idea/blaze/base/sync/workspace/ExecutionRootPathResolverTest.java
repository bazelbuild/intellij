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
package com.google.idea.blaze.base.sync.workspace;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.*;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Units tests for {@link ExecutionRootPathResolver}. */
@RunWith(JUnit4.class)
public class ExecutionRootPathResolverTest extends BlazeTestCase {

  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(new File("/path/to/root"));
  private static final String EXECUTION_ROOT = "/path/to/_bazel_user/1234bf129e/execroot/__main__";
  private static final String OUTPUT_BASE = "/path/to/_bazel_user/1234bf129e";

  private static final List<String> WORKSPACES = Arrays.asList(null, "external_workspace");
  private static final List<WorkspacePath> WORKSPACE_PATHS = createWorkspacePaths();

  private static final TargetName SIMPLE_TARGET = TargetName.create("simple_target");
  private static final TargetName ADVANCED_TARGET = TargetName.create("advanced_target");
  private static final List<TargetName> TARGET_NAMES = List.of(SIMPLE_TARGET, ADVANCED_TARGET);

  private ExecutionRootPathResolver pathResolver;

  @NotNull
  private static List<WorkspacePath> createWorkspacePaths() {
    WorkspacePath rootPath = new WorkspacePath("");
    WorkspacePath fooPath = new WorkspacePath("foo");
    WorkspacePath fooBarPath = new WorkspacePath("foo/bar");

    return List.of(rootPath, fooPath, fooBarPath);
  }

  private static String getStripPrefix(TargetName targetName) {
    String simpleStripPrefix = "include";
    String advancedStripPrefix = "src/main/cpp/include";

    if (targetName.equals(SIMPLE_TARGET)) {
      return simpleStripPrefix;
    } else if (targetName.equals(ADVANCED_TARGET)) {
      return advancedStripPrefix;
    } else {
      throw new IllegalArgumentException("Unexpected targetName");
    }
  }

  private static TargetIdeInfo getTargetIdeInfo(TargetName targetName) {
    String stripPrefix = getStripPrefix(targetName);

    return TargetIdeInfo.builder()
        .setCInfo(CIdeInfo.builder().setStripIncludePrefix(stripPrefix))
        .build();
  }

  @NotNull
  private static TargetMap getTargetMap() {
    ImmutableMap.Builder<TargetKey, TargetIdeInfo> builder = new ImmutableMap.Builder<>();

    for (String workspaceName : WORKSPACES) {
      for (WorkspacePath workspacePath : WORKSPACE_PATHS) {
        for (TargetName targetName : TARGET_NAMES) {
          builder.put(
              TargetKey.forPlainTarget(Label.create(workspaceName, workspacePath, targetName)),
              getTargetIdeInfo(targetName));
        }
      }
    }

    return new TargetMap(builder.build());
  }

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    pathResolver =
        new ExecutionRootPathResolver(
            new BazelBuildSystemProvider(),
            WORKSPACE_ROOT,
            new File(EXECUTION_ROOT),
            new File(OUTPUT_BASE),
            new WorkspacePathResolverImpl(WORKSPACE_ROOT),
            getTargetMap());
  }

  @Test
  public void testExternalWorkspacePathRelativeToOutputBase() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(new ExecutionRootPath("external/guava/src"));
    assertThat(files).containsExactly(new File(OUTPUT_BASE, "external/guava/src"));
  }

  @Test
  public void testGenfilesPathRelativeToExecRoot() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(
            new ExecutionRootPath("bazel-out/crosstool/genfiles/res/normal"));
    assertThat(files)
        .containsExactly(new File(EXECUTION_ROOT, "bazel-out/crosstool/genfiles/res/normal"));
  }

  @Test
  public void testMainWorkspacePathsRelativeToWorkspaceRoot() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(new ExecutionRootPath("tools/fast"));
    assertThat(files).containsExactly(WORKSPACE_ROOT.fileForPath(new WorkspacePath("tools/fast")));
  }

  @Test
  public void testGenfilesPathWithDifferentConfigSettingStillResolves() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(
            new ExecutionRootPath("bazel-out/arm-linux-fastbuild/genfiles/res/normal"));
    assertThat(files)
        .containsExactly(
            new File(EXECUTION_ROOT, "bazel-out/arm-linux-fastbuild/genfiles/res/normal"));
  }

  @Test
  public void testIllegalWorkspacePaths() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(new ExecutionRootPath("tools/fast/:include"));
    assertThat(files).isEmpty();
  }

  @Test
  public void testVirtualIncludes() {
    for (String workspaceName : WORKSPACES) {
      for (WorkspacePath workspacePath : WORKSPACE_PATHS) {
        for (TargetName targetName : TARGET_NAMES) {
          String workspaceNameString = workspaceName != null ? workspaceName : "";

          ExecutionRootPath generatedPath = new ExecutionRootPath(Path.of(
              "bazel-out/k8-fastbuild/bin",
              (workspaceName == null ? "" : ExecutionRootPathResolver.externalPath.getPath()),
              workspaceNameString,
              workspacePath.toString(),
              VirtualIncludesHandler.VIRTUAL_INCLUDES_DIRECTORY.toString(),
              targetName.toString()).toFile());

          ImmutableList<File> files =
              pathResolver.resolveToIncludeDirectories(generatedPath);

          String expectedPath = Path.of(workspaceNameString,
              workspacePath.toString(),
              getStripPrefix(targetName)).toString();

          if (workspaceName != null) {
            // check external workspace
            assertThat(files).containsExactly(
                Path.of(OUTPUT_BASE, ExecutionRootPathResolver.externalPath.getPath(), expectedPath)
                    .toFile());
          } else {
            // check local
            assertThat(files).containsExactly(
                WORKSPACE_ROOT.fileForPath(new WorkspacePath(expectedPath)));
          }
        }
      }
    }
  }
}
