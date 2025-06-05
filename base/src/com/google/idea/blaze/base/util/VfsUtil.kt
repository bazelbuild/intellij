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
package com.google.idea.blaze.base.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

object VfsUtil {

  @TestOnly
  @JvmStatic
  fun getVfsChildrenAsList(root: Path): List<Path> {
    val virtualRoot = VirtualFileManager.getInstance().findFileByNioPath(root) ?: return emptyList()
    return getVfsChildrenAsSequence(virtualRoot).toList()
  }

  fun getVfsChildrenAsSequence(dir: VirtualFile): Sequence<Path> = sequence {
    val persistentFS = PersistentFS.getInstance()
    if (!persistentFS.wereChildrenAccessed(dir)) return@sequence

    for (name in persistentFS.listPersisted(dir)) {
      val child = dir.findChild(name) ?: continue

      if (child.isDirectory) {
        yieldAll(getVfsChildrenAsSequence(child))
      } else {
        yield(Path.of(child.path))
      }
    }
  }
}