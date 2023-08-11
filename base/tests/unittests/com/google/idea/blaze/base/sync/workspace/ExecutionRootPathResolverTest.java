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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.*;
import com.intellij.openapi.util.registry.Registry;
import java.io.IOException;
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
  private static final TargetName TARGET_WITH_INCLUDE_PREFIX = TargetName.create("include_prefix");
  private static final List<TargetName> TARGET_NAMES = List.of(SIMPLE_TARGET, ADVANCED_TARGET);
  private static final String INCLUDE_PREFIX = "generated-include-prefix";

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
    } else if (targetName.equals(TARGET_WITH_INCLUDE_PREFIX)) {
      return simpleStripPrefix;
    } else {
      throw new IllegalArgumentException("Unexpected targetName");
    }
  }

  private static TargetIdeInfo getTargetIdeInfo(TargetName targetName) {
    String stripPrefix = getStripPrefix(targetName);

    CIdeInfo.Builder cIdeInfoBuilder = CIdeInfo.builder().setStripIncludePrefix(stripPrefix);
    if (targetName.equals(TARGET_WITH_INCLUDE_PREFIX)) {
      cIdeInfoBuilder.setIncludePrefix(INCLUDE_PREFIX);
    }

    return TargetIdeInfo.builder()
        .setCInfo(cIdeInfoBuilder)
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

    builder.put(
        TargetKey.forPlainTarget(
            Label.create(WORKSPACES.get(0), WORKSPACE_PATHS.get(0), TARGET_WITH_INCLUDE_PREFIX)),
        getTargetIdeInfo(TARGET_WITH_INCLUDE_PREFIX));

    return new TargetMap(builder.build());
  }

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    Registry.get("bazel.sync.resolve.virtual.includes").setValue(true);

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

  @Test
  public void testVirtualIncludesWithIncludePrefix() {
    File fileToBeResolved = Path.of(
        "bazel-out/k8-fastbuild/bin",
        WORKSPACE_PATHS.get(0).toString(),
        VirtualIncludesHandler.VIRTUAL_INCLUDES_DIRECTORY.toString(),
        TARGET_WITH_INCLUDE_PREFIX.toString(),
        INCLUDE_PREFIX).toFile();

    ExecutionRootPath generatedPath = new ExecutionRootPath(fileToBeResolved);

    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(generatedPath);

    // check that include path for target with "include_prefix" attribute is resolved to execution root
    assertThat(files).containsExactly(
        new File(EXECUTION_ROOT, fileToBeResolved.toString()));
  }

  @Test
  public void testExternalWorkspaceSymlinkToProject() throws IOException {
    Path expectedPath = Path.of(WORKSPACE_ROOT.toString(), "guava", "src");

    Path pathMock = mock(Path.class);
    when(pathMock.toRealPath()).thenReturn(expectedPath);

    File output = spy(new File("external/guava/src"));

    // some hacky mocking to bypass several ifs
    when(output.isAbsolute()).thenReturn(false, true);
    when(output.toPath()).thenReturn(pathMock);

    ExecutionRootPath pathUnderTest = new ExecutionRootPath(output);

    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(pathUnderTest);

    assertThat(files).containsExactly(WORKSPACE_ROOT.fileForPath(new WorkspacePath("guava/src")));
  }
}
