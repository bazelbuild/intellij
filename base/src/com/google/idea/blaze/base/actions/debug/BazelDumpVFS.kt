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

import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.util.VfsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import kotlin.io.path.extension

class BazelDumpVFS : BazelDebugAction() {

  override suspend fun exec(project: Project, data: BlazeProjectData): String {
    val root = data.blazeInfo.executionRoot
      ?: fail("no execution root found")

    val virtualRoot = VirtualFileManager.getInstance().findFileByNioPath(root.toPath())
      ?: fail("no virtual file found for workspace root")

    val builder = StringBuilder()
    val histogram = mutableMapOf<String, Long>()

    for (child in VfsUtil.getVfsChildrenAsSequence(virtualRoot)) {
      histogram[child.extension] = histogram.getOrDefault(child.extension, 0) + 1L
      builder.appendLine(child.toString())
    }

    builder.appendLine("################################## EXECROOT MAP ################################")

    for ((ext, size) in histogram.entries.sortedByDescending { it.value }) {
      builder.appendLine(String.format("%6d - %s", size, ext))
    }

    return builder.toString()
  }
}
