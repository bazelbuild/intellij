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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class WorkspacePathResolverImplTest extends BlazeTestCase {
  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(new File("/path/to/root"));
  private static final String EXECUTION_ROOT = "/path/to/_blaze_user/1234bf129e/root";

  private static final BlazeRoots BLAZE_CITC_ROOTS = new BlazeRoots(
    new File(EXECUTION_ROOT),
    ImmutableList.of(WORKSPACE_ROOT.directory()),
    new ExecutionRootPath("blaze-out/crosstool/bin"),
    new ExecutionRootPath("blaze-out/crosstool/genfiles")
  );

  @Test
  public void testResolveToIncludeDirectories() {
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(WORKSPACE_ROOT, BLAZE_CITC_ROOTS);
    ImmutableList<File> files = workspacePathResolver.resolveToIncludeDirectories(new ExecutionRootPath("tools/fast"));
    assertThat(files).containsExactly(new File("/path/to/root/tools/fast"));
  }

  @Test
  public void testResolveToIncludeDirectoriesForExecRootPath() {
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(WORKSPACE_ROOT, BLAZE_CITC_ROOTS);
    ImmutableList<File> files = workspacePathResolver.resolveToIncludeDirectories(
      new ExecutionRootPath("blaze-out/crosstool/bin/tools/fast")
    );
    assertThat(files).containsExactly(new File("/path/to/root/blaze-out/crosstool/bin/tools/fast"));
  }
}
