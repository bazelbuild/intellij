/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.storage

import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.jvm.Throws

class AspectWriterImpl : AspectWriter {

  override fun name(): String {
    return "Default Aspects"
  }

  override fun write(dst: Path, project: Project) {
    try {
      AspectWriter.copyAspects(AspectWriterImpl::class.java, dst, AspectRepositoryProvider.ASPECT_DIRECTORY);
    } catch (e: IOException) {
      throw SyncFailedException("Could not copy aspects", e)
    }
  }
}
