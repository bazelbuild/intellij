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

import com.google.common.io.ByteSource
import com.google.idea.blaze.base.sync.SyncScope
import com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider.ASPECT_QSYNC_DIRECTORY
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Files.createDirectories
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Extension point for writing aspect files to the workspace.
 */
interface AspectWriter {

  companion object {
    val EP_NAME = ExtensionPointName.Companion.create<AspectWriter>("com.google.idea.blaze.AspectWriter");

    @JvmStatic
    @Throws(IOException::class)
    fun copyAspects(ctx: Class<*>, dst: Path, src: String) = copyAspectsImpl(ctx, dst, src)

    @JvmStatic
    fun readAspect(ctx: Class<*>, dir: String, file: String): ByteSource = readAspectImpl(ctx, dir, file)
  }

  /**
   * Name of the aspects to copy, used for debugging and logging.
   */
  fun name(): String

  /**
   * Write all aspect files to the destination directory.
   * Files are resolved relative to the destination directory.
   */
  @Throws(SyncScope.SyncFailedException::class)
  fun write(dst: Path, project: Project)
}

private fun copyAspectsImpl(ctx: Class<*>, dst: Path, src: String) {
  assert(!src.endsWith('/'))

  val classLoader = ctx.getClassLoader() ?: throw IOException("no classloader for: $ctx")

  val manifest = classLoader.getResourceAsStream("$src/manifest") ?: throw IOException("could not find manifest in $src")
  val files = manifest.reader().useLines { it.toList() }

  for (file in files) {
    val dstFile = dst.resolve(file.removePrefix("/"))
    dstFile.parent?.let(Files::createDirectories)

    val srcStream = classLoader.getResourceAsStream("$src$file") ?: throw IOException("could not find file from manifest: $file")

    srcStream.use { srcStream ->
      Files.newOutputStream(
        dstFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
      ).use { dstStream -> srcStream.transferTo(dstStream) }
    }
  }
}

private fun readAspectImpl(ctx: Class<*>, dir: String, file: String): ByteSource {
  val resource = "$dir/$file"

  return object : ByteSource() {

    override fun openStream(): InputStream {
      val classLoader = ctx.getClassLoader() ?: throw IOException("no classloader for: $ctx")
      return classLoader.getResourceAsStream(resource) ?: throw IOException("aspect file not found: $resource")
    }
  }
}