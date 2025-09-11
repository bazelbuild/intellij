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
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSpecificSwitchBuilder
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind
import java.io.File

class CoptsSysrootProcessor : CoptsProcessor.Transform() {

  override fun flags(kind: OCCompilerKind): ImmutableList<String> {
    return when (kind) {
      GCCCompilerKind, ClangCompilerKind -> ImmutableList.of("--sysroot")
      // TODO: add support for MSVC
      else -> ImmutableList.of()
    }
  }

  override fun apply(
    value: String,
    sink: CompilerSpecificSwitchBuilder,
    resolver: ExecutionRootPathResolver
  ) {
    val file = File(value)

    if (file.isAbsolute) {
      sink.withSysroot(file.path)
    } else {
      sink.withSysroot(resolver.resolveExecutionRootPath(ExecutionRootPath(file)).path)
    }
  }
}