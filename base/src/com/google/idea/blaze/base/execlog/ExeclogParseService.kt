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

import com.google.idea.common.util.Datafiles
import com.google.idea.common.util.RunJarService
import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import java.nio.file.Path

val PARSER_JAR_PATH: Path by Datafiles.resolveLazy("bazel/execlog_parser.jar")

@Service(Service.Level.PROJECT)
class ExeclogParseService(private val project: Project) {

  companion object {
    @Throws(ExecutionException::class)
    suspend fun run(project: Project, logPath: String): String = project.service<ExeclogParseService>().parse(logPath)
  }

  @Throws(ExecutionException::class)
  private suspend fun parse(logPath: String): String {
    return withBackgroundProgress(project, "Parsing Execution Log: $logPath") {
      val output = RunJarService.capture(PARSER_JAR_PATH, "--log_path", logPath)
      if (output.exitCode != 0) {
        throw ExecutionException("Execlog parser failed with exit code ${output.exitCode}: ${output.stderr}")
      }

      output.stdout
    }
  }
}
