package com.google.idea.blaze.cpp.sync

import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.idea.blaze.base.filecache.FileCache
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetKey
import com.google.idea.blaze.base.logging.LoggedDirectoryProvider
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.scopes.TimingScope
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.sync.SyncMode
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asStream

private val LOG = logger<VirtualIncludesCacheService>()

private const val VIRTUAL_INCLUDES_CACHE_DIR = "_virtual_includes_cache"
private const val VIRTUAL_INCLUDES_BAZEL_DIR = "_virtual_includes"
private const val VIRTUAL_IMPORTS_BAZEL_DIR = "_virtual_imports"

@Service(Service.Level.PROJECT)
@Suppress("UnstableApiUsage")
class VirtualIncludesCacheService(private val project: Project) {

  companion object {
    @JvmStatic
    fun of(project: Project): VirtualIncludesCacheService = project.service()

    @JvmStatic
    val enabled: Boolean get() = Registry.`is`("bazel.cc.virtual.includes.cache.enabled")
  }

  val cacheDirectory: Path by lazy {
    // TODO: do we need a read action here? is the lazy initialization a race?
    val importSettings = BlazeImportSettingsManager.getInstance(project).importSettings

    if (importSettings != null) {
      BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve(VIRTUAL_INCLUDES_CACHE_DIR)
    } else {
      Path.of(project.basePath, VIRTUAL_INCLUDES_CACHE_DIR)
    }
  }

  // tracks targets which are actually stored in the cache
  private val cacheTracker: MutableSet<HashCode> = mutableSetOf()

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
    cacheTracker.clear()

    if (Files.exists(cacheDirectory)) {
      // I have a feeling, that this will be really slow on Windows, so let's try to avoid this
      NioFiles.deleteRecursively(cacheDirectory)
    }

    LOG.trace("cleared virtual includes cache")
  }

  @Synchronized
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  fun refresh(projectData: BlazeProjectData, nonInc: Boolean) {
    if (nonInc) {
      clear()
    } else {
      // only reset in memory data on incremental sync
      cacheTracker.clear()
    }

    for ((key, target) in projectData.targetMap.map()) {
      refreshTarget(projectData, key, target)
    }

    // one could consider removing unused directories on an incremental sync, but it's faster not to do so :)
  }

  private fun refreshTarget(projectData: BlazeProjectData, key: TargetKey, target: TargetIdeInfo) {
    val info = target.getcIdeInfo() ?: return

    val targetCacheDirectory = key.cacheDirectory()

    if (!info.includePrefix().isEmpty() || !info.stripIncludePrefix().isEmpty()) {
      // if there is an include_prefix or a strip_include_prefix there should be _virtual_includes directory
      val bazelBin = projectData.blazeInfo.blazeBinDirectory.toPath()

      // if there is no _virtual_includes directory the target most likely has no headers
      val virtualIncludesDir = findVirtualIncludesDirectory(bazelBin, key.label) ?: return

      try {
        Files.createDirectories(targetCacheDirectory)
        NioFiles.copyRecursively(virtualIncludesDir, targetCacheDirectory)
      } catch (e: IOException) {
        LOG.warn("failed to copy _virtual_includes directory for ${key.label}", e)
      }

      cacheTracker.add(key.cacheHashCode())
      LOG.trace { "${key.cacheHashCode()} - ${key.label} -> copied _virtual_includes directory" }
    } else {
      // if there is no _virtual_include directory, adopt all generated headers
      val headers = (info.headers().asSequence() + info.textualHeaders().asSequence()).filter { it.isGenerated }.toList()
      if (headers.isEmpty()) return

      for (header in headers) {
        val relativePath = stripVirtualPrefix(header.relativePath) ?: continue
        val path = targetCacheDirectory.resolve(relativePath)

        try {
          Files.createDirectories(path.parent)
          Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { dst ->
            projectData.artifactLocationDecoder.resolveOutput(header).inputStream.use { src ->
              src.transferTo(dst)
            }
          }
        } catch (e: IOException) {
          LOG.warn("failed to copy generated header ${header.relativePath} for ${key.label}", e)
        }
      }

      cacheTracker.add(key.cacheHashCode())
      LOG.trace { "${key.cacheHashCode()} - ${key.label} -> copied generated headers (${headers.size})" }
    }
  }

  @Synchronized
  fun collectVirtualIncludes(targetIdeInfo: TargetIdeInfo): Stream<String> {
    val info = targetIdeInfo.getcIdeInfo() ?: return Stream.empty()

    return sequence {
      for (dep in info.transitiveDependencies()) {
        val hash = dep.cacheHashCode()
        if (!cacheTracker.contains(hash)) continue

        yield(cacheDirectory.resolve(hash.toString()).toString())
      }
    }.asStream()
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

  /**
   * If the CcInfo was not created by a cc_xxx rule the include prefix and
   * strip include prefix will not be populated. This heuristic tries to
   * detect these cases and adjust the path accordingly.
   */
  private fun stripVirtualPrefix(path: String): String? {
    val elements = path.split('/')

    // drop virtual imports, they are not supported yet
    if (elements.contains(VIRTUAL_IMPORTS_BAZEL_DIR)) return null

    val index = elements.indexOf(VIRTUAL_INCLUDES_BAZEL_DIR)

    // no virtual includes or invalid index
    if (index < 0 || index + 2 >= elements.size) return path

    // +2 to drop the _virtual_include directory and the target name
    return elements.subList(index + 2, elements.size).joinToString("/")
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
    if (!VirtualIncludesCacheService.enabled || !syncMode.involvesBlazeBuild()) return
    LOG.trace("refresh requested onSync: $syncMode")

    Scope.push(parentCtx) { ctx ->
      ctx.push(TimingScope(name, TimingScope.EventType.Other))
      VirtualIncludesCacheService.of(project).refresh(projectData, nonInc = syncMode == SyncMode.FULL)
    }
  }

  override fun refreshFiles(
    project: Project,
    context: BlazeContext,
    buildOutputs: BlazeBuildOutputs,
  ) {
    if (!VirtualIncludesCacheService.enabled) return
    LOG.trace("refresh files requested")

    val projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() ?: return
    VirtualIncludesCacheService.of(project).refresh(projectData, nonInc = false)
  }

  override fun initialize(project: Project) {}
}

private class VirtualIncludesCacheLoggedDirectory : LoggedDirectoryProvider {

  override fun getLoggedDirectory(project: Project): Optional<LoggedDirectoryProvider.LoggedDirectory> {
    if (!VirtualIncludesCacheService.enabled) return Optional.empty()

    return Optional.of(
      LoggedDirectoryProvider.LoggedDirectory.builder()
        .setPath(VirtualIncludesCacheService.of(project).cacheDirectory)
        .setOriginatingIdePart("CLwB Virtual Includes Cache")
        .setPurpose("Cache _virtual_includes directories and generated headers")
        .build()
    )
  }
}
