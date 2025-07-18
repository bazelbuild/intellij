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
package com.google.idea.blaze.base.actions

import com.google.idea.blaze.base.logging.LoggedDirectoryProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

private val LOG = Logger.getInstance(BazelDumpCacheDirectories::class.java)

class BazelDumpCacheDirectories : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    if (project == null) {
      LOG.warn("no open project found")
      return
    }

    LOG.info("################################### CACHE DIRS #################################")
    for (provider in LoggedDirectoryProvider.EP_NAME.extensionList) {
      val directory = provider.getLoggedDirectory(project).getOrNull() ?: continue

      val exists = Files.exists(directory.path())
      val size = if (exists) collectDirectorySize(directory.path()) else null

      LOG.info("Directory: ${directory.path().toAbsolutePath()}")
      LOG.info("-> purpose: ${directory.purpose()}")
      LOG.info("->  origin: ${directory.originatingIdePart()}")
      LOG.info("->  exists: $exists")

      if (size != null) {
        LOG.info("->    size: ${size.size}B")
        LOG.info("->   items: ${size.items}")
      }
    }
    LOG.info("################################################################################")
  }
}

private data class DirectorySize(val items: Long, val size: Long)

private fun collectDirectorySize(path: Path): DirectorySize? {
  var items = 0L
  var size = 0L

  try {
    for (item in Files.walk(path)) {
      items++
      size += Files.size(item)
    }
  } catch (e: IOException) {
    LOG.warn("failed to collect directory size $path", e)
  }

  return DirectorySize(items, size)
}