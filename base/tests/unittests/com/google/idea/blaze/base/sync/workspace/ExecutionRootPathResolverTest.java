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
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Units tests for {@link ExecutionRootPathResolver}. */
@RunWith(JUnit4.class)
public class ExecutionRootPathResolverTest extends BlazeTestCase {

  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(new File("/path/to/root"));
  private static final String EXECUTION_ROOT = "/path/to/_blaze_user/1234bf129e/root";

  private static final BlazeRoots BLAZE_ROOTS =
      new BlazeRoots(
          new File(EXECUTION_ROOT),
          ImmutableList.of(WORKSPACE_ROOT.directory()),
          new ExecutionRootPath("blaze-out/crosstool/bin"),
          new ExecutionRootPath("blaze-out/crosstool/genfiles"),
          null);

  private final ExecutionRootPathResolver pathResolver =
      new ExecutionRootPathResolver(
          BLAZE_ROOTS, new WorkspacePathResolverImpl(WORKSPACE_ROOT, BLAZE_ROOTS));

  @Test
  public void testExternalWorkspacePathRelativeToExecRoot() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(new ExecutionRootPath("external/guava/src"));
    assertThat(files).containsExactly(new File(EXECUTION_ROOT, "external/guava/src"));
  }

  @Test
  public void testGenfilesPathRelativeToExecRoot() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(
            new ExecutionRootPath("blaze-out/crosstool/genfiles/res/normal"));
    assertThat(files)
        .containsExactly(new File(EXECUTION_ROOT, "blaze-out/crosstool/genfiles/res/normal"));
  }

  @Test
  public void testNonOutputPathsRelativeToWorkspaceRoot() {
    ImmutableList<File> files =
        pathResolver.resolveToIncludeDirectories(new ExecutionRootPath("tools/fast"));
    assertThat(files).containsExactly(WORKSPACE_ROOT.fileForPath(new WorkspacePath("tools/fast")));
  }
}
