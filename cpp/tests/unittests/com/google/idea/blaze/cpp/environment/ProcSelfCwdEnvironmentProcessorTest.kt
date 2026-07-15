/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.environment

import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider
import com.google.idea.blaze.base.ideinfo.TargetMap
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolverImpl
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path

private val WORKSPACE_ROOT = WorkspaceRoot(File("/path/to/root"))

private val EXECUTION_ROOT = Path.of("/path/to/_bazel_user/1234bf129e/execroot/__main__")
private val OUTPUT_BASE = Path.of("/path/to/_bazel_user/1234bf129e")

/** Unit tests for [ProcSelfCwdEnvironmentProcessor]. */
@RunWith(JUnit4::class)
class ProcSelfCwdEnvironmentProcessorTest : BlazeTestCase() {

    private val processor = ProcSelfCwdEnvironmentProcessor()
    private lateinit var resolver: ExecutionRootPathResolver

    override fun initTest(applicationServices: Container, projectServices: Container) {
        resolver = ExecutionRootPathResolverImpl(
            BazelBuildSystemProvider(),
            WORKSPACE_ROOT,
            EXECUTION_ROOT.toFile(),
            OUTPUT_BASE.toFile(),
            WorkspacePathResolverImpl(WORKSPACE_ROOT),
            TargetMap(ImmutableMap.of())
        )
    }

    @Test
    fun testRewritesProcSelfCwdValueToAbsolutePath() {
        val environment = mutableMapOf("QNX_HOST" to "/proc/self/cwd/external/qnx/host")
        processor.process(environment, resolver)

        val expected = OUTPUT_BASE.resolve("external/qnx/host").toAbsolutePath().toString()
        assertThat(environment).containsEntry("QNX_HOST", expected)
    }

    @Test
    fun testLeavesNonPathValuesUntouched() {
        val environment = mutableMapOf("FOO" to "bar", "REL" to "some/relative/value")
        val expected = ImmutableMap.copyOf(environment)
        processor.process(environment, resolver)
        assertThat(environment).isEqualTo(expected)
    }

    @Test
    fun testLeavesGenuineAbsolutePathsUntouched() {
        val environment = mutableMapOf("PATH" to "/usr/bin:/bin")
        val expected = ImmutableMap.copyOf(environment)
        processor.process(environment, resolver)
        assertThat(environment).isEqualTo(expected)
    }
}