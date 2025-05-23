package com.google.idea.blaze.cpp

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.command.info.BlazeInfo
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetKey
import com.google.idea.blaze.base.ideinfo.TargetMap
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.scopes.TimingScope
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver
import com.jetbrains.cidr.lang.OCFileTypeHelpers
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Predicate

private const val GEN_HEADER_ROOT_SEARCH_LIMIT = 50

fun getValidHeaderRoots(
  parentContext: BlazeContext,
  projectData: BlazeProjectData,
  toolchainLookupMap: ImmutableMap<TargetKey, CToolchainIdeInfo>,
  targetFilter: Predicate<TargetIdeInfo>,
  executionRootPathResolver: ExecutionRootPathResolver,
): ImmutableSet<Path> = Scope.push<ImmutableSet<Path>>(parentContext) { ctx ->
  ctx.push(TimingScope("Resolve header include roots", TimingScope.EventType.Other))

  val paths = collectExecutionRootPaths(projectData.getTargetMap(), targetFilter, toolchainLookupMap)

  val builder = ImmutableSet.builder<Path>()

  for (path in paths) {
    val possibleDirectories = executionRootPathResolver.resolveToIncludeDirectories(path).map(File::toPath)

    for (directory in possibleDirectories) {
      if (!path.isOutputDirectory(projectData.blazeInfo)) {
        builder.add(directory)
      } else if (genRootMayContainHeaders(directory)) {
        builder.add(directory)
      }
    }
  }

  val roots = builder.build()

  // val actual = HeaderRootTrimmer.getValidRoots(ctx, projectData, toolchainLookupMap, targetFilter, executionRootPathResolver)
  // actual.forEach { assert(roots.contains(it.toPath())) { "missing root $it" } }

  roots
}

private fun collectExecutionRootPaths(
  targetMap: TargetMap,
  targetFilter: Predicate<TargetIdeInfo>,
  toolchainLookupMap: ImmutableMap<TargetKey, CToolchainIdeInfo>,
): Set<ExecutionRootPath> {
  val paths = mutableSetOf<ExecutionRootPath>()

  for (target in targetMap.targets()) {
    if (!targetFilter.test(target)) continue
    val ideInfo = target.getcIdeInfo() ?: continue

    paths.addAll(ideInfo.transitiveSystemIncludeDirectories)
    paths.addAll(ideInfo.transitiveIncludeDirectories)
    paths.addAll(ideInfo.transitiveQuoteIncludeDirectories)
  }

  // Builtin includes should not be added to the switch builder, because CLion discovers builtin include paths during
  // the compiler info collection, and therefore it would be safe to filter these header roots. But this would make
  // the filter stricter, and it is unclear if this would affect any users.
  // NOTE: if the toolchain uses an external sysroot, CLion might not be able to discover the all builtin include paths.
  for (toolchain in toolchainLookupMap.values) {
    paths.addAll(toolchain.builtInIncludeDirectories)
  }

  return paths
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


private fun ExecutionRootPath.isOutputDirectory(info: BlazeInfo): Boolean {
  return ExecutionRootPath.isAncestor(info.blazeGenfiles, this, false)
      || ExecutionRootPath.isAncestor(info.blazeBin, this, false)
}