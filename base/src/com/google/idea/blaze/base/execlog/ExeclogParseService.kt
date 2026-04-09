/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.execlog

import com.google.common.io.MoreFiles
import com.google.idea.common.util.Datafiles
import com.google.idea.common.util.FileUtil
import com.google.idea.common.util.RunJarService
import com.intellij.execution.ExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ExeclogParseService>()

val PARSER_JAR_PATH: Path by Datafiles.resolveLazy("bazel/execlog_parser.jar")

@Service(Service.Level.PROJECT)
class ExeclogParseService(private val project: Project) : Disposable {

  companion object {
    @Throws(ExecutionException::class)
    suspend fun run(project: Project, logPath: String): Path = project.service<ExeclogParseService>().parse(logPath)

    fun release(project: Project, path: Path) = project.service<ExeclogParseService>().release(path)
  }

  private val trackedFiles: MutableSet<Path> = ConcurrentHashMap.newKeySet()

  @Throws(ExecutionException::class)
  private suspend fun parse(logPath: String): Path {
    return withBackgroundProgress(project, "Parsing Execution Log: $logPath") {
      val outputFile = createOutputFile(logPath)

      FileUtil.deleteOnException(outputFile) {
        val result = RunJarService.capture(
          PARSER_JAR_PATH,
          "--log_path", logPath,
          "--output_path", outputFile.toString()
        )

        if (result.exitCode != 0) {
          throw ExecutionException("Execlog parser failed with exit code ${result.exitCode}: ${result.stderr}")
        }
      }

      outputFile.also { trackedFiles.add(it) }
    }
  }

  @Throws(ExecutionException::class)
  private suspend fun createOutputFile(logPath: String): Path {
    val name = MoreFiles.getNameWithoutExtension(Path.of(logPath))

    return try {
      withContext(Dispatchers.IO) {
        Files.createTempFile(name, "." + ExeclogBundleProvider.EXECLOG_FILE_EXTENSION)
      }
    } catch (e: IOException) {
      throw ExecutionException("Could not create temporary file", e)
    }
  }

  private fun tryDelete(path: Path) {
    try {
      Files.deleteIfExists(path)
    } catch (e: IOException) {
      LOG.warn("failed to delete temporary file: $path", e)
    }
  }

  private fun release(path: Path) {
    if (trackedFiles.remove(path)) {
      tryDelete(path)
    }
  }

  override fun dispose() {
    for (path in trackedFiles) {
      tryDelete(path)
    }
    trackedFiles.clear()
  }
}
