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
package com.google.idea.common.util

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.APP)
class RunJarService {

  companion object {
    @Throws(ExecutionException::class)
    suspend fun run(jar: Path, vararg args: String): OSProcessHandler = service<RunJarService>().run(jar, *args)
  }

  @Throws(ExecutionException::class)
  private suspend fun run(jar: Path, vararg args: String): OSProcessHandler {
    val java = try {
      findJavaExecutable()
    } catch (e: IOException) {
      throw ExecutionException("could not find java executable", e)
    }

    val cmdLine = GeneralCommandLine()
      .withExePath(java.toString())
      .withParameters("-jar", jar.toString(), *args)

    return withContext(Dispatchers.IO) {
      OSProcessHandler(cmdLine)
    }
  }

  @Throws(IOException::class)
  private fun findJavaExecutable(): Path {
    val home = System.getProperty("java.home") ?: throw IOException("java.home not found")
    val java = Path.of(home, "bin", "java")

    if (!Files.exists(java)) throw IOException("java executable not found: $java")
    if (!Files.isExecutable(java)) throw IOException("java executable is not executable: $java")

    return java
  }
}