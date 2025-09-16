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
package com.google.idea.blaze.cpp.copts

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver
import com.jetbrains.cidr.lang.workspace.compiler.*
import java.nio.file.Path

abstract class CoptsIncludeProcessor : CoptsProcessor.Transform() {

  override fun apply(value: String, sink: CompilerSpecificSwitchBuilder, resolver: ExecutionRootPathResolver) {
    val path = Path.of(value)

    if (path.isAbsolute) {
      apply(path, sink)
    } else {
      resolver.resolveToIncludeDirectories(ExecutionRootPath.create(path)).forEach {
        apply(it.toPath(), sink)
      }
    }
  }

  protected abstract fun apply(path: Path, sink: CompilerSpecificSwitchBuilder)

  class Default : CoptsIncludeProcessor() {

    override fun flags(kind: OCCompilerKind?): ImmutableList<String> {
      return when (kind) {
        GCCCompilerKind, ClangCompilerKind -> ImmutableList.of("-I")
        MSVCCompilerKind -> ImmutableList.of("/I")
        ClangClCompilerKind -> ImmutableList.of("/I", "/clang:-I")
        else -> ImmutableList.of()
      }
    }

    override fun apply(path: Path, sink: CompilerSpecificSwitchBuilder) {
      sink.withIncludePath(path.toString())
    }
  }

  class System : CoptsIncludeProcessor() {

    override fun flags(kind: OCCompilerKind?): ImmutableList<String> {
      return when (kind) {
        GCCCompilerKind, ClangCompilerKind -> ImmutableList.of("-isystem")
        MSVCCompilerKind -> ImmutableList.of("/external:I")
        ClangClCompilerKind -> ImmutableList.of("/external:I", "/clang:-isystem")
        else -> ImmutableList.of()
      }
    }

    override fun apply(path: Path, sink: CompilerSpecificSwitchBuilder) {
      sink.withSystemIncludePath(path.toString())
    }
  }

  class Quote : CoptsIncludeProcessor() {

    override fun flags(kind: OCCompilerKind?): ImmutableList<String> {
      return when (kind) {
        GCCCompilerKind, ClangCompilerKind -> ImmutableList.of("-iquote")
        ClangClCompilerKind -> ImmutableList.of("/clang:-iquote")
        else -> ImmutableList.of()
      }
    }

    override fun apply(path: Path, sink: CompilerSpecificSwitchBuilder) {
      sink.withQuoteIncludePath(path.toString())
    }
  }
}
