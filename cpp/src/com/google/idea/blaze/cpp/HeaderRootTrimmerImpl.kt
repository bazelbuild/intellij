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
package com.google.idea.blaze.cpp

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.buildview.pushJob
import com.google.idea.blaze.base.command.info.BlazeInfo
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetKey
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.scopes.TimingScope
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver
import com.google.idea.blaze.cpp.sync.CcIncludesCacheService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.cidr.lang.OCFileTypeHelpers
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate

private const val GEN_HEADER_ROOT_SEARCH_LIMIT = 50

private val LOG = logger<HeaderRootTrimmerImpl>()

class HeaderRootTrimmerImpl(private val scope: CoroutineScope) : HeaderRootTrimmer {

  override fun getValidHeaderRoots(
    parentContext: BlazeContext,
    projectData: BlazeProjectData,
    toolchainLookupMap: ImmutableMap<TargetKey, CToolchainIdeInfo>,
    targetFilter: Predicate<TargetIdeInfo>,
    executionRootPathResolver: ExecutionRootPathResolver,
  ): ImmutableSet<Path> = Scope.push<ImmutableSet<Path>>(parentContext) { ctx ->
    ctx.push(TimingScope("Resolve header include roots", TimingScope.EventType.Other))

    val paths = collectExecutionRootPaths(projectData, targetFilter, toolchainLookupMap)

    val builder = ImmutableSet.builder<Path>()
    runBlocking {
      scope.launch {
        ctx.pushJob("HeaderRootTrimmer") {
          val results = paths.map { root ->
            async(Dispatchers.IO) {
              collectHeaderRoots(executionRootPathResolver, root, projectData)
            }
          }

          results.awaitAll().forEach(builder::addAll)
        }
      }.join()
    }

    builder.build().also(::logHeaderRootPaths)
  }
}

private fun collectExecutionRootPaths(
  projectData: BlazeProjectData,
  targetFilter: Predicate<TargetIdeInfo>,
  toolchainLookupMap: ImmutableMap<TargetKey, CToolchainIdeInfo>,
): Set<ExecutionRootPath> {
  val paths = mutableSetOf<ExecutionRootPath>()

  for (target in projectData.targetMap.targets()) {
    if (!targetFilter.test(target)) continue
    val compilationCtx = target.getcIdeInfo()?.compilationContext() ?: continue

    paths.addAll(compilationCtx.includes())
    paths.addAll(compilationCtx.quoteIncludes())
    paths.addAll(compilationCtx.systemIncludes())
  }

  // Builtin includes should not be added to the switch builder, because CLion discovers builtin include paths during
  // the compiler info collection, and therefore it would be safe to filter these header roots. But this would make
  // the filter stricter, and it is unclear if this would affect any users.
  // NOTE: if the toolchain uses an external sysroot, CLion might not be able to discover the all builtin include paths.
  for (toolchain in toolchainLookupMap.values) {
    paths.addAll(toolchain.builtInIncludeDirectories())
  }

  if (CcIncludesCacheService.enabled) {
    paths.removeIf { it.inBazelBin(projectData.blazeInfo) }
  }

  return paths
}

private fun collectHeaderRoots(
  executionRootPathResolver: ExecutionRootPathResolver,
  path: ExecutionRootPath,
  projectData: BlazeProjectData,
): List<Path> {
  val possibleDirectories = if (CcIncludesCacheService.enabled) {
    listOf(executionRootPathResolver.resolveExecutionRootPath(path).toPath())
  } else {
    executionRootPathResolver.resolveToIncludeDirectories(path).map(File::toPath)
  }

  val allowBazelBin =
    Registry.`is`("bazel.cpp.sync.allow.bazel.bin.header.search.path") && !CcIncludesCacheService.enabled

  val result = mutableListOf<Path>()
  for (directory in possibleDirectories) {
    val add = when {
      // only allow bazel-bin as a header search path when the registry key is set
      path.isBazelBin(projectData.blazeInfo) -> allowBazelBin

      // if it is not an output directory, there should be now big binary artifacts
      !path.isOutputDirectory(projectData.blazeInfo) -> true

      // if it is an output directory, but there are headers, we need to allow it
      genRootMayContainHeaders(directory) -> true

      else -> false
    }

    if (add) {
      result.add(directory)
    }
  }

  return result
}

private fun genRootMayContainHeaders(directory: Path): Boolean {
  val worklist: Queue<Path> = ArrayDeque()
  worklist.add(directory)

  var totalDirectoriesChecked = 0
  while (!worklist.isEmpty()) {
    totalDirectoriesChecked++
    if (totalDirectoriesChecked > GEN_HEADER_ROOT_SEARCH_LIMIT) {
      return true
    }

    val dir = worklist.poll()
    if (!Files.exists(dir)) {
      continue
    }

    for (child in Files.list(dir)) {
      if (Files.isDirectory(child)) {
        worklist.add(child)
        continue
      }
      if (OCFileTypeHelpers.isHeaderFile(child.fileName.toString())) {
        return true
      }
    }
  }

  return false
}

private fun logHeaderRootPaths(roots: ImmutableSet<Path>) {
  LOG.trace("############################## VALID HEADER ROOTS ##############################")
  roots.forEach { LOG.trace(it.toString()) }
  LOG.trace("################################################################################")
}

private fun ExecutionRootPath.isBazelBin(info: BlazeInfo): Boolean {
  return ExecutionRootPath.pathsEqual(info.blazeBin, this)
}

private fun ExecutionRootPath.inBazelBin(info: BlazeInfo): Boolean {
  return ExecutionRootPath.isAncestor(info.blazeBin, this, false)
}

private fun ExecutionRootPath.isOutputDirectory(info: BlazeInfo): Boolean {
  return ExecutionRootPath.isAncestor(info.blazeGenfiles, this, false)
      || ExecutionRootPath.isAncestor(info.blazeBin, this, false)
}