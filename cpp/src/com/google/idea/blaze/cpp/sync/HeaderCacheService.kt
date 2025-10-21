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
package com.google.idea.blaze.cpp.sync

import com.google.idea.blaze.base.filecache.FileCache
import com.google.idea.blaze.base.ideinfo.ArtifactLocation
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo
import com.google.idea.blaze.base.ideinfo.TargetKey
import com.google.idea.blaze.base.logging.LoggedDirectoryProvider
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

private val LOG = logger<HeaderCacheService>()

private const val CACHE_DIRECTORY = "headerCache"

@Service(Service.Level.PROJECT)
@Suppress("UnstableApiUsage")
class HeaderCacheService(private val project: Project) {

  companion object {
    const val ENABLED_KEY: String = "bazel.cpp.header.cache.enabled"

    @JvmStatic
    fun of(project: Project): HeaderCacheService = project.service()

    @JvmStatic
    val enabled: Boolean get() = Registry.`is`(ENABLED_KEY)
  }

  val cacheDirectory: Path by lazy {
    // TODO: do we need a read action here? is the lazy initialization a race?
    val importSettings = BlazeImportSettingsManager.getInstance(project).importSettings

    if (importSettings != null) {
      BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve(CACHE_DIRECTORY)
    } else {
      project.getProjectDataPath(CACHE_DIRECTORY)
    }
  }

  // tracks headers which are actually stored in the cache
  private val cacheTracker: MutableSet<String> = mutableSetOf()

  private fun TargetKey.cacheDirectory(): Path {
    // TODO: use different cache directories depending on the configuration
    return cacheDirectory.resolve("default")
  }

  @Synchronized
  @RequiresReadLockAbsence
  @RequiresBackgroundThread
  @Throws(IOException::class)
  private fun clear() {
    cacheTracker.clear()

    if (Files.exists(cacheDirectory)) {
      // On windows this could be replace with a rename and asynchronous delete for better performance.
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
  }

  private fun refreshTarget(projectData: BlazeProjectData, key: TargetKey, target: TargetIdeInfo) {
    val info = target.getcIdeInfo() ?: return

    val targetCacheDirectory = key.cacheDirectory()

    for (header in info.compilationContext().headers()) {
      // check if the header is inside bazel-bin
      if (!isInBazelBin(header)) continue

      // check if the header is already present in the cache
      if (!cacheTracker.add(header.relativePath())) continue

      val path = targetCacheDirectory.resolve(header.relativePath())

      try {
        Files.createDirectories(path.parent)

        // NOTE: this also copies symlinked headers like in _virtual_includes
        Files.newOutputStream(
          path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
        ).use { dst ->
          projectData.artifactLocationDecoder.resolveOutput(header).inputStream.use { src ->
            src.transferTo(dst)
          }
        }
      } catch (e: IOException) {
        cacheTracker.remove(header.relativePath())
        LOG.warn("failed to copy generated header ${header.relativePath()} for ${key.label}", e)
      }
    }
  }

  @Synchronized
  fun resolve(target: TargetKey, executionRootPath: ExecutionRootPath): Optional<Path> {
    val path = executionRootPath.path()
    if (!isInBazelBin(path)) return Optional.empty()

    return Optional.of(
      if (path.nameCount <= 3) {
        target.cacheDirectory()
      } else {
        target.cacheDirectory().resolve(path.subpath(3, path.nameCount))
      }
    )
  }

  private fun isInBazelBin(path: Path): Boolean {
    return path.nameCount >= 3
        && path.getName(0).toString() == "bazel-out"
        && path.getName(2).toString() == "bin";
  }

  private fun isInBazelBin(location: ArtifactLocation): Boolean {
    return location.rootPath().isNotBlank() && isInBazelBin(Path.of(location.rootPath()))
  }
}

private class HeaderFileCache : FileCache {

  override fun getName(): String = "Header Cache"

  override fun onSync(
    project: Project,
    parentCtx: BlazeContext,
    projectViewSet: ProjectViewSet,
    projectData: BlazeProjectData,
    oldProjectData: BlazeProjectData?,
    syncMode: SyncMode,
  ) {
    if (!HeaderCacheService.enabled || !syncMode.involvesBlazeBuild()) return
    LOG.trace("refresh requested onSync: $syncMode")

    Scope.push(parentCtx) { ctx ->
      ctx.push(TimingScope(name, TimingScope.EventType.Other))
      HeaderCacheService.of(project).refresh(projectData, nonInc = syncMode == SyncMode.FULL)
    }
  }

  override fun refreshFiles(
    project: Project,
    context: BlazeContext,
    buildOutputs: BlazeBuildOutputs,
  ) {
    if (!HeaderCacheService.enabled) return
    LOG.trace("refresh files requested")

    val projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() ?: return
    HeaderCacheService.of(project).refresh(projectData, nonInc = false)
  }

  override fun initialize(project: Project) {}
}

private class HeaderCacheLoggedDirectory : LoggedDirectoryProvider {

  override fun getLoggedDirectory(project: Project): Optional<LoggedDirectoryProvider.LoggedDirectory> {
    if (!HeaderCacheService.enabled) return Optional.empty()

    return Optional.of(
      LoggedDirectoryProvider.LoggedDirectory.builder()
        .setPath(HeaderCacheService.of(project).cacheDirectory)
        .setOriginatingIdePart("CLwB Header Cache")
        .setPurpose("Cache headers from the execution root")
        .build()
    )
  }
}
