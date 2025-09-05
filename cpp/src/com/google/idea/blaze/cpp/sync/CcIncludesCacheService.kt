package com.google.idea.blaze.cpp.sync

import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.idea.blaze.base.filecache.FileCache
import com.google.idea.blaze.base.ideinfo.ArtifactLocation
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
import com.intellij.util.applyIf
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

private val LOG = logger<CcIncludesCacheService>()

private const val CC_INCLUDES_CACHE_DIR = "_cc_includes_cache"
private const val VIRTUAL_INCLUDES_BAZEL_DIR = "_virtual_includes"
private const val VIRTUAL_IMPORTS_BAZEL_DIR = "_virtual_imports"
private const val EXTERNAL_BAZEL_DIR = "external"

@Service(Service.Level.PROJECT)
@Suppress("UnstableApiUsage")
class CcIncludesCacheService(private val project: Project) {

  companion object {
    @JvmStatic
    fun of(project: Project): CcIncludesCacheService = project.service()

    @JvmStatic
    val enabled: Boolean get() = Registry.`is`("bazel.cc.includes.cache.enabled")
  }

  val cacheDirectory: Path by lazy {
    // TODO: do we need a read action here? is the lazy initialization a race?
    val importSettings = BlazeImportSettingsManager.getInstance(project).importSettings

    if (importSettings != null) {
      BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve(CC_INCLUDES_CACHE_DIR)
    } else {
      Path.of(project.basePath, CC_INCLUDES_CACHE_DIR)
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
      // I have a feeling that this will be really slow on Windows, so let's try to avoid this
      NioFiles.deleteRecursively(cacheDirectory)
    }

    LOG.trace("cleared cc includes cache")
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

    if (info.ruleContext().includePrefix().isNotEmpty() || info.ruleContext().stripIncludePrefix().isNotEmpty()) {
      // if there is an include_prefix or a strip_include_prefix there should be _virtual_includes directory
      val bazelBin = projectData.blazeInfo.blazeBinDirectory.toPath()

      // if there is no _virtual_includes directory, the target most likely has no headers
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
      val headers = (info.compilationContext().directHeaders()).filter { it.isGenerated }.toList()
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
  fun getIncludePaths(targetIdeInfo: TargetIdeInfo): Stream<String> {
    val info = targetIdeInfo.getcIdeInfo() ?: return Stream.empty()

    return sequence {
      for (dep in info.dependencies()) {
        val hash = dep.cacheHashCode()
        if (!cacheTracker.contains(hash)) continue

        yield(cacheDirectory.resolve(hash.toString()).toString())
      }
    }.asStream()
  }

  private fun findVirtualIncludesDirectory(bazelBin: Path, label: Label): Path? {
    val path = bazelBin
      .applyIf(label.externalWorkspaceName() != null) { resolve("external").resolve(label.externalWorkspaceName()) }
      .resolve(label.blazePackage().relativePath())
      .resolve(VIRTUAL_INCLUDES_BAZEL_DIR)
      .resolve(label.targetName().toString())

    if (!Files.exists(path)) {
      return null
    }

    return path
  }

  @Synchronized
  fun resolve(target: TargetKey, artifact: ArtifactLocation): Path? {
    if (!artifact.isGenerated) return null

    val path = target.cacheDirectory().resolve(stripVirtualPrefix(artifact.relativePath))
    if (!Files.exists(path)) return null

    return path
  }

  /**
   * If the CcInfo was not created by a cc_xxx rule, the include prefix and
   * strip include prefix will not be populated. This heuristic tries to
   * detect these cases and adjust the path accordingly.
   */
  private fun stripVirtualPrefix(path: String): String? {
    var elements = path.split('/')

    // drop virtual imports, they are not supported yet
    if (elements.contains(VIRTUAL_IMPORTS_BAZEL_DIR)) return null

    // drop the external directory and the repository name
    if (elements.size > 2 && elements[0] == EXTERNAL_BAZEL_DIR) {
      elements = elements.drop(2)
    }

    val index = elements.indexOf(VIRTUAL_INCLUDES_BAZEL_DIR)

    // drop the _virtual_include directory and the package name
    if (index >= 0 && index + 2 < elements.size) {
      elements = elements.drop(2)
    }

    return elements.joinToString("/")
  }
}

private class CcIncludesFileCache : FileCache {

  override fun getName(): String = "Cc Includes Cache"

  override fun onSync(
    project: Project,
    parentCtx: BlazeContext,
    projectViewSet: ProjectViewSet,
    projectData: BlazeProjectData,
    oldProjectData: BlazeProjectData?,
    syncMode: SyncMode,
  ) {
    if (!CcIncludesCacheService.enabled || !syncMode.involvesBlazeBuild()) return
    LOG.trace("refresh requested onSync: $syncMode")

    Scope.push(parentCtx) { ctx ->
      ctx.push(TimingScope(name, TimingScope.EventType.Other))
      CcIncludesCacheService.of(project).refresh(projectData, nonInc = syncMode == SyncMode.FULL)
    }
  }

  override fun refreshFiles(
    project: Project,
    context: BlazeContext,
    buildOutputs: BlazeBuildOutputs,
  ) {
    if (!CcIncludesCacheService.enabled) return
    LOG.trace("refresh files requested")

    val projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() ?: return
    CcIncludesCacheService.of(project).refresh(projectData, nonInc = false)
  }

  override fun initialize(project: Project) {}
}

private class CcIncludesCacheLoggedDirectory : LoggedDirectoryProvider {

  override fun getLoggedDirectory(project: Project): Optional<LoggedDirectoryProvider.LoggedDirectory> {
    if (!CcIncludesCacheService.enabled) return Optional.empty()

    return Optional.of(
      LoggedDirectoryProvider.LoggedDirectory.builder()
        .setPath(CcIncludesCacheService.of(project).cacheDirectory)
        .setOriginatingIdePart("CLwB Cc Includes Cache")
        .setPurpose("Cache includes from the execution root")
        .build()
    )
  }
}
