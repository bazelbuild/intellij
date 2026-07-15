/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.environment;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolverImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ProcSelfCwdEnvironmentProcessor}. */
@RunWith(JUnit4.class)
public class ProcSelfCwdEnvironmentProcessorTest extends BlazeTestCase {

  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(new File("/path/to/root"));
  private static final String EXECUTION_ROOT = "/path/to/_bazel_user/1234bf129e/execroot/__main__";
  private static final String OUTPUT_BASE = "/path/to/_bazel_user/1234bf129e";

  private final ProcSelfCwdEnvironmentProcessor processor = new ProcSelfCwdEnvironmentProcessor();
  private ExecutionRootPathResolver resolver;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    resolver = new ExecutionRootPathResolverImpl(
        new BazelBuildSystemProvider(),
        WORKSPACE_ROOT,
        new File(EXECUTION_ROOT),
        new File(OUTPUT_BASE),
        new WorkspacePathResolverImpl(WORKSPACE_ROOT),
        new TargetMap(ImmutableMap.of())
    );
  }

  @Test
  public void testRewritesProcSelfCwdValueToAbsolutePath() {
    final var result = processor.process(ImmutableMap.of("QNX_HOST", "/proc/self/cwd/external/qnx/host"), resolver);
    assertThat(result).containsEntry("QNX_HOST", new File(OUTPUT_BASE, "external/qnx/host").getAbsolutePath());
  }

  @Test
  public void testLeavesNonPathValuesUntouched() {
    final var environment = ImmutableMap.of("FOO", "bar", "REL", "some/relative/value");
    assertThat(processor.process(environment, resolver)).isEqualTo(environment);
  }

  @Test
  public void testLeavesGenuineAbsolutePathsUntouched() {
    final var environment = ImmutableMap.of("PATH", "/usr/bin:/bin");
    assertThat(processor.process(environment, resolver)).isEqualTo(environment);
  }
}
