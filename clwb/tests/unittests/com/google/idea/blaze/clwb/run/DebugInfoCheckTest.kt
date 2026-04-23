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
package com.google.idea.blaze.clwb.run

import com.google.common.truth.Truth.assertThat
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DebugInfoCheckTest {

  private fun assertPasses(arguments: List<String>, compilerKind: OCCompilerKind?) {
    assertThat(checkDebugInfoPresent(arguments, compilerKind)).isTrue()
  }

  private fun assertFails(arguments: List<String>, compilerKind: OCCompilerKind?) {
    assertThat(checkDebugInfoPresent(arguments, compilerKind)).isFalse()
  }

  // -- GCC tests --

  @Test
  fun gcc_withDashG_passes() {
    assertPasses(listOf("/usr/bin/gcc", "-c", "-g", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_withDashG2_passes() {
    assertPasses(listOf("/usr/bin/gcc", "-c", "-g2", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_withDashG3_passes() {
    assertPasses(listOf("/usr/bin/gcc", "-c", "-g3", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_withDashGdwarf4_passes() {
    assertPasses(listOf("/usr/bin/gcc", "-c", "-gdwarf-4", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_withDashGgdb_passes() {
    assertPasses(listOf("/usr/bin/gcc", "-c", "-ggdb", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_noDebugFlag_fails() {
    assertFails(listOf("/usr/bin/gcc", "-c", "-O2", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_withDashG0_fails() {
    assertFails(listOf("/usr/bin/gcc", "-c", "-g0", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_g2ThenG0_lastWins_fails() {
    assertFails(listOf("/usr/bin/gcc", "-c", "-g2", "-g0", "main.c"), GCCCompilerKind)
  }

  @Test
  fun gcc_g0ThenG2_lastWins_passes() {
    assertPasses(listOf("/usr/bin/gcc", "-c", "-g0", "-g2", "main.c"), GCCCompilerKind)
  }

  // -- Clang tests --

  @Test
  fun clang_withDashGlldb_passes() {
    assertPasses(listOf("/usr/bin/clang", "-c", "-glldb", "main.c"), ClangCompilerKind)
  }

  @Test
  fun clang_withDashG_passes() {
    assertPasses(listOf("/usr/bin/clang", "-c", "-g", "main.c"), ClangCompilerKind)
  }

  @Test
  fun clang_noDebugFlag_fails() {
    assertFails(listOf("/usr/bin/clang", "-c", "-O2", "main.c"), ClangCompilerKind)
  }

  // -- MSVC tests --

  @Test
  fun msvc_withZ7_passes() {
    assertPasses(listOf("cl.exe", "/c", "/Z7", "main.c"), MSVCCompilerKind)
  }

  @Test
  fun msvc_withZi_passes() {
    assertPasses(listOf("cl.exe", "/c", "/Zi", "main.c"), MSVCCompilerKind)
  }

  @Test
  fun msvc_withZI_passes() {
    assertPasses(listOf("cl.exe", "/c", "/ZI", "main.c"), MSVCCompilerKind)
  }

  @Test
  fun msvc_noDebugFlag_fails() {
    assertFails(listOf("cl.exe", "/c", "/O2", "main.c"), MSVCCompilerKind)
  }

  // -- Clang-CL tests --

  @Test
  fun clangCl_withDashG_passes() {
    assertPasses(listOf("clang-cl", "/c", "-g", "main.c"), ClangClCompilerKind)
  }

  @Test
  fun clangCl_withDashG2_passes() {
    assertPasses(listOf("clang-cl", "/c", "-g2", "main.c"), ClangClCompilerKind)
  }

  @Test
  fun clangCl_noDebugFlag_fails() {
    assertFails(listOf("clang-cl", "/c", "/O2", "main.c"), ClangClCompilerKind)
  }

  // -- Unknown compiler (null) tests --

  @Test
  fun unknown_withDashG_passes() {
    assertPasses(listOf("/some/compiler", "-c", "-g", "main.c"), null)
  }

  @Test
  fun unknown_withZ7_passes() {
    assertPasses(listOf("/some/compiler", "/c", "/Z7", "main.c"), null)
  }

  @Test
  fun unknown_noDebugFlag_fails() {
    assertFails(listOf("/some/compiler", "-c", "main.c"), null)
  }

  @Test
  fun unknown_withDashG0_fails() {
    assertFails(listOf("/some/compiler", "-c", "-g0", "main.c"), null)
  }
}
