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
package com.google.idea.sdkcompat.clion

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import com.jetbrains.cidr.lang.workspace.compiler.OCCompiler
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerCommandLineShortener
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerId
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool
import com.jetbrains.cidr.lang.workspace.compiler.resolver.OCCompilerResolverCache
import java.io.File

// #api262: `OCCompilerKind.getId` was added in 2026.3
abstract class OCCompilerDelegate(val delegate: OCCompilerKind) : OCCompilerKind by delegate {

  override fun getId(): OCCompilerId = delegate.id

  override fun skipLanguageNotRelatedSwitches(switches: List<String?>): List<String?> {
    return delegate.skipLanguageNotRelatedSwitches(switches)
  }

  override fun fixPchSwitches(switches: List<String?>): List<String?> {
    return delegate.fixPchSwitches(switches)
  }

  override fun getCommandLineShortener(): OCCompilerCommandLineShortener {
    return delegate.getCommandLineShortener()
  }

  override fun getCompilerInstance(
    project: Project,
    compilerExecutable: File,
    compilerWorkingDirectory: File,
    environment: CidrToolEnvironment,
    tempFilesPool: TempFilesPool,
    cache: OCCompilerResolverCache
  ): OCCompiler {
    return delegate.getCompilerInstance(
      project,
      compilerExecutable,
      compilerWorkingDirectory,
      environment,
      tempFilesPool,
      cache
    )
  }
}
