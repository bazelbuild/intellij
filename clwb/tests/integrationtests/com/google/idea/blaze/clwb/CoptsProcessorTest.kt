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
package com.google.idea.blaze.clwb

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.clwb.base.ExecutionRootPathResolverStub
import com.google.common.collect.ImmutableList
import com.google.idea.blaze.cpp.copts.CoptsProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.ClangSwitchBuilder
import com.jetbrains.cidr.lang.workspace.compiler.ClangClSwitchBuilder
import com.jetbrains.cidr.lang.workspace.compiler.GCCSwitchBuilder
import com.jetbrains.cidr.lang.workspace.compiler.MSVCSwitchBuilder
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path

private val ALL_COMPILER = listOf(ClangCompilerKind, GCCCompilerKind, ClangClCompilerKind, MSVCCompilerKind)

@RunWith(JUnit4::class)
class CoptsProcessorTest : BasePlatformTestCase() {

  private fun doTest(
    compilers: List<OCCompilerKind>,
    copts: List<String>,
    expected: List<String>,
  ) {
    for (compiler in compilers) {
      val builder = when (compiler) {
        ClangCompilerKind -> ClangSwitchBuilder()
        GCCCompilerKind -> GCCSwitchBuilder()
        ClangClCompilerKind -> ClangClSwitchBuilder()
        MSVCCompilerKind -> MSVCSwitchBuilder()
        else -> error("unknown compiler: $compiler")
      }

      CoptsProcessor.apply(
        ImmutableList.copyOf(copts),
        compiler,
        builder,
        ExecutionRootPathResolverStub(Path.of("/path/to/project")),
      )

      assertThat(builder.buildRaw()).containsExactlyElementsIn(expected)
    }
  }

  @Test
  fun `drop empty options`() = doTest(
    compilers = ALL_COMPILER,
    copts = listOf("", "-Wall", "", "", "-Wextra", "-Werror", ""),
    expected = listOf("-Wall", "-Wextra", "-Werror"),
  )

  @Test
  fun `expand gcc or clang includes`() = doTest(
    compilers = listOf(GCCCompilerKind, ClangCompilerKind),
    copts = listOf(
      "-I/absolut/path",
      "-Iinclude/default",
      "-isysteminclude/system",
      "-iquoteinclude/quote",
    ),
    expected = listOf(
      "-I/absolut/path",
      "-I/path/to/project/include/default",
      "-isystem/path/to/project/include/system",
      "-iquote/path/to/project/include/quote",
    ),
  )

  @Test
  fun `expand split flags`() = doTest(
    compilers = listOf(GCCCompilerKind, ClangCompilerKind),
    copts = listOf(
      "-I", "/absolut/path",
      "-I", "include/default",
      "-isystem", "include/system",
      "-iquote", "include/quote",
    ),
    expected = listOf(
      "-I/absolut/path",
      "-I/path/to/project/include/default",
      "-isystem/path/to/project/include/system",
      "-iquote/path/to/project/include/quote",
    ),
  )

  @Test
  fun `expand clang cl includes`() = doTest(
    compilers = listOf(ClangClCompilerKind),
    copts = listOf(
      "/I/absolut/path_msvc",
      "/Iinclude/default_msvc",
      "/clang:-I/absolut/path",
      "/clang:-Iinclude/default",
      "/clang:-isysteminclude/system",
      "/clang:-iquoteinclude/quote",
    ),
    expected = listOf(
      "-I/absolut/path_msvc", // that's how ClangClSwitchBuilder works, idk if this is correct or not
      "-I/path/to/project/include/default_msvc",
      "-I/absolut/path",
      "-I/path/to/project/include/default",
      "/clang:-isystem/path/to/project/include/system",
      "/clang:-iquote/path/to/project/include/quote",
    ),
  )

  @Test
  fun `expand msvc includes`() = doTest(
    compilers = listOf(MSVCCompilerKind),
    copts = listOf(
      "/I/absolut/path",
      "/Iinclude/default",
      "/external:I/absolut/path",
      "/external:Iinclude/default",
    ),
    expected = listOf(
      "/I/absolut/path",
      "/I/path/to/project/include/default",
      "/I/absolut/path",
      "/I/path/to/project/include/default",
    ),
  )
}
