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
package com.google.idea.blaze.cpp

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import com.jetbrains.cidr.lang.workspace.compiler.BasicCompilerCommandLineShortener
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompiler
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompiler
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerCommandLineShortener
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKindProvider
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool
import java.io.File

/**
 * An [OCCompilerKind] wrapper that disables response file usage.
 *
 * Bazel's compiler wrapper scripts do not support response files (`@file` arguments).
 * This kind delegates all behavior to the underlying [delegate] kind, but overrides
 * [getCompilerInstance] to return a [BazelGCCCompiler] (which disables response file
 * shortening) and [getCommandLineShortener] to return a no-op shortener.
 *
 * Yes, there are two places to define the command line shortener :(
 */
open class BazelCompilerKind(val delegate: OCCompilerKind) : OCCompilerKind by delegate {

  override fun getCommandLineShortener(): OCCompilerCommandLineShortener = BasicCompilerCommandLineShortener()

  override fun getCompilerInstance(
    project: Project,
    compilerExecutable: File,
    compilerWorkingDirectory: File,
    environment: CidrToolEnvironment,
    tempFilesPool: TempFilesPool,
  ): OCCompiler = object : GCCCompiler(compilerExecutable, compilerWorkingDirectory, environment, tempFilesPool) {

    override fun getCommandLineShortener(): OCCompilerCommandLineShortener = BasicCompilerCommandLineShortener()
  }

  override fun equals(other: Any?): Boolean =
    delegate == other || (other is BazelCompilerKind && delegate == other.delegate)

  override fun hashCode(): Int = delegate.hashCode()

  override fun toString(): String = "Bazel($delegate)"
}

/** Bazel-specific GCC compiler kind that disables response files. */
object BazelGCCCompilerKind : BazelCompilerKind(GCCCompilerKind)

/** Bazel-specific Clang compiler kind that disables response files. */
object BazelClangCompilerKind : BazelCompilerKind(ClangCompilerKind)

/** Provider to register the compiler kinds with CLion. */
class BazelCompilerKindProvider : OCCompilerKindProvider {

  override fun getCompilerKinds(): List<OCCompilerKind> = listOf(BazelGCCCompilerKind, BazelClangCompilerKind)
}
