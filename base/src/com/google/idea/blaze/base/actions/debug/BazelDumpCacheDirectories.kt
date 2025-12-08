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
package com.google.idea.blaze.base.actions.debug

import com.google.idea.blaze.base.logging.LoggedDirectoryProvider
import com.google.idea.blaze.base.model.BlazeProjectData
import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

class BazelDumpCacheDirectories : BazelDebugAction() {

  override suspend fun exec(project: Project, data: BlazeProjectData): String {
    val builder = StringBuilder()

    for (provider in LoggedDirectoryProvider.EP_NAME.extensionList) {
      val directory = provider.getLoggedDirectory(project).getOrNull() ?: continue

      val exists = Files.exists(directory.path())
      val size = if (exists) collectDirectorySize(directory.path()) else null

      builder.appendLine("Directory: ${directory.path().toAbsolutePath()}")
      builder.appendLine("-> purpose: ${directory.purpose()}")
      builder.appendLine("->  origin: ${directory.originatingIdePart()}")
      builder.appendLine("->  exists: $exists")

      if (size != null) {
        builder.appendLine("->    size: ${size.size}B")
        builder.appendLine("->   items: ${size.items}")
      }
    }

    return builder.toString()
  }
}

private data class DirectorySize(val items: Long, val size: Long)

private fun collectDirectorySize(path: Path): DirectorySize? {
  var items = 0L
  var size = 0L

  try {
    Files.walk(path).use { stream ->
      stream.forEach { item ->
        items++
        size += Files.size(item)
      }
    }
  } catch (_: IOException) { // ignore exception, report 0
    items = 0
    size = 0
  }

  return DirectorySize(items, size)
}
