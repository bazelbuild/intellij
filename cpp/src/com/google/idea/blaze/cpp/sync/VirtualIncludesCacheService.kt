package com.google.idea.blaze.cpp.sync

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.filecache.FileCache
import com.google.idea.blaze.base.logging.LoggedDirectoryProvider
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.scopes.TimingScope
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.sync.SyncMode
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.common.hash.Hashing
import com.google.common.hash.HashFunction
import com.google.common.hash.HashCode
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetKey
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.jetbrains.rd.util.getOrCreate
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.stream.Stream
import kotlin.collections.iterator
import kotlin.streams.asStream

private val LOG = logger<VirtualIncludesCacheService>()

private const val VIRTUAL_INCLUDES_CACHE_DIR = "_virtual_includes_cache"
private const val VIRTUAL_INCLUDES_BAZEL_DIR = "_virtual_includes"

@Service(Service.Level.PROJECT)
@Suppress("UnstableApiUsage")
class VirtualIncludesCacheService(private val project: Project) {

  companion object {
    @JvmStatic
    fun of(project: Project): VirtualIncludesCacheService = project.service()

    val enabled: Boolean get() = Registry.`is`("bazel.cc.virtual.includes.cache")
  }

  private val cacheDirectory: Path by lazy {
    val importSettings = BlazeImportSettingsManager.getInstance(project).importSettings

    if (importSettings != null) {
      BlazeDataStorage.getProjectDataDir(importSettings).toPath()
        .resolve(VIRTUAL_INCLUDES_CACHE_DIR)
    } else {
      Path.of(project.basePath, VIRTUAL_INCLUDES_CACHE_DIR)
    }
  }

  // tracks targets which are actually stored in the cache
  private val cacheTracker: MutableSet<HashCode> = mutableSetOf()

  // recursive dependency cache to resolve all includes required for target
  private val dependencyCache: MutableMap<HashCode, MutableSet<HashCode>> = mutableMapOf()

  private val sha256: HashFunction by lazy { Hashing.sha256() }

  private fun TargetKey.cacheHashCode(): HashCode {
    return sha256.hashString(label.toString(), Charset.defaultCharset())
  }

  private fun TargetKey.cacheDirectory(): Path {
    return cacheDirectory.resolve(cacheHashCode().toString())
  }

  @Synchronized
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  @Throws(IOException::class)
  fun clear() {
    dependencyCache.clear()
    cacheTracker.clear()

    if (Files.exists(cacheDirectory)) {
      NioFiles.deleteRecursively(cacheDirectory)
    }
  }

  @Synchronized
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  @Throws(IOException::class)
  fun refresh(projectData: BlazeProjectData, nonInc: Boolean) {
    if (nonInc) clear()

    val bazelBin = projectData.blazeInfo.blazeBinDirectory.toPath()

    for ((key, target) in projectData.targetMap.map()) {
      val info = target.getcIdeInfo() ?: continue

      dependencyCache
        .getOrPut(key.cacheHashCode()) { mutableSetOf() }
        .addAll(target.dependencies.asSequence().map { it.targetKey.cacheHashCode() })

      if (!info.includePrefix.isEmpty() || !info.stripIncludePrefix.isEmpty()) {
        val virtualIncludesDir = findVirtualIncludesDirectory(bazelBin, key.label)

        if (virtualIncludesDir != null) {
          val targetCacheDirectory = key.cacheDirectory()
          Files.createDirectories(targetCacheDirectory)

          NioFiles.copyRecursively(virtualIncludesDir, targetCacheDirectory)
          cacheTracker.add(key.cacheHashCode())
        } else {
          // TODO: warn
        }

        continue
      }

      val headers =
        (info.headers.asSequence() + info.textualHeaders.asSequence()).filter { it.isGenerated }
          .toList()
      if (headers.isEmpty()) continue

      val targetCacheDirectory = key.cacheDirectory()
      cacheTracker.add(key.cacheHashCode())

      for (header in headers) {
        val path = targetCacheDirectory.resolve(header.relativePath)
        Files.createDirectories(path.parent)

        Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
          .use { dstStream ->
            projectData.artifactLocationDecoder.resolveOutput(header).inputStream.use { srcStream ->
              srcStream.transferTo(dstStream)
            }
          }
      }
    }
  }

  fun collectVirtualIncludes(targetIdeInfo: TargetIdeInfo): Stream<String> {
    val visited = mutableSetOf<HashCode>()
    val frontier = mutableListOf(targetIdeInfo.key.cacheHashCode())

    return sequence {
      while (frontier.isNotEmpty()) {
        val code = frontier.removeFirst()
        if (!visited.add(code)) continue

        yield(code)

        frontier.addAll(dependencyCache[code] ?: continue)
      }
    }
      .filter { cacheTracker.contains(it) }
      .map { cacheDirectory.resolve(it.toString()).toString() }
      .asStream()
  }

  private fun findVirtualIncludesDirectory(bazelBin: Path, label: Label): Path? {
    val path = bazelBin
      .resolve(label.blazePackage().relativePath())
      .resolve(VIRTUAL_INCLUDES_BAZEL_DIR)
      .resolve(label.targetName().toString())

    if (!Files.exists(path)) {
      return null
    }

    return path
  }
}

private class VirtualIncludesFileCache : FileCache {

  override fun getName(): String = "Virtual Includes Cache"

  override fun onSync(
    project: Project,
    parentCtx: BlazeContext,
    projectViewSet: ProjectViewSet,
    projectData: BlazeProjectData,
    oldProjectData: BlazeProjectData?,
    syncMode: SyncMode,
  ) {
    if (!VirtualIncludesCacheService.enabled) return

    Scope.push(parentCtx) { ctx ->
      ctx.push(TimingScope(name, TimingScope.EventType.Other))

      try {
        VirtualIncludesCacheService.of(project)
          .refresh(projectData, nonInc = syncMode == SyncMode.FULL)
      } catch (e: IOException) {
        IssueOutput.warn("$name refresh failed").withDescription(e.toString()).submit(ctx)
      }
    }
  }

  override fun refreshFiles(
    project: Project,
    context: BlazeContext,
    buildOutputs: BlazeBuildOutputs,
  ) {
    if (!VirtualIncludesCacheService.enabled) return

    val projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() ?: return

    try {
      VirtualIncludesCacheService.of(project).refresh(projectData, nonInc = false)
    } catch (e: IOException) {
      IssueOutput.warn("$name refresh failed").withDescription(e.toString()).submit(context)
    }
  }

  override fun initialize(project: Project) {}
}

private class VirtualIncludesCacheLoggedDirectory : LoggedDirectoryProvider {

  override fun getLoggedDirectory(project: Project?): Optional<LoggedDirectoryProvider.LoggedDirectory?> {
    TODO("Not yet implemented")
  }
}
