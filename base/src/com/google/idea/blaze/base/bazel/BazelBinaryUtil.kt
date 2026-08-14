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
package com.google.idea.blaze.base.bazel

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.util.system.OS
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Resolves the binary used to invoke Bazel and checks whether it can actually be executed.
 * 
 * This is the single source of truth for the binary path; both the exec service and the sync
 * pre-flight check go through it.
 */
object BazelBinaryUtil {

  @JvmStatic
  fun resolvePath(project: Project): String {
    return resolvePath(ProjectViewManager.getInstance(project).getProjectViewSet())
  }

  @JvmStatic
  fun resolvePath(projectViewSet: ProjectViewSet?): String {
    projectViewSet?.getScalarValue(BazelBinarySection.KEY)?.orElse(null)?.let {
      return it.path
    }

    return BlazeUserSettings.getInstance().bazelBinaryPath
  }

  /** Conservatively checks whether the binary can be executed. */
  @JvmStatic
  fun validate(path: String, workspaceRoot: Path?): Problem? {
    return validate(path, workspaceRoot, PathEnvironmentVariableUtil.getPathVariableValue())
  }

  @JvmStatic
  @VisibleForTesting
  fun validate(path: String, workspaceRoot: Path?, pathVariableValue: String?): Problem? {
    if (path.isBlank()) {
      return Problem.NOT_CONFIGURED
    }

    if (validateOnPath(path, pathVariableValue)) {
      return null
    }

    return validateFile(path, workspaceRoot)
  }

  private fun validateFile(path: String, workspaceRoot: Path?): Problem? {
    val path = try {
      Path.of(path)
    } catch (_: InvalidPathException) {
      return Problem.INVALID_PATH
    }

    var candidates = when {
      path.isAbsolute -> listOf(path)
      workspaceRoot != null -> listOf(path.toAbsolutePath(), workspaceRoot.resolve(path))
      else -> listOf(path.toAbsolutePath())
    }

    candidates = candidates.flatMap(::resolveWindowsExtension)

    candidates = candidates.filter { Files.exists(it) }
    if (candidates.isEmpty()) {
      return Problem.DOES_NOT_EXIST
    }

    candidates = candidates.filter { !Files.isDirectory(it) }
    if (candidates.isEmpty()) {
      return Problem.IS_A_DIRECTORY
    }

    candidates = candidates.filter { Files.isExecutable(it) }
    if (candidates.isEmpty()) {
      return Problem.NOT_EXECUTABLE
    }

    return null
  }

  /**
   * On Windows CreateProcess appends the executable extensions to a path without one, so
   * C:\tools\bazel launches C:\tools\bazel.exe
   */
  private fun resolveWindowsExtension(path: Path): List<Path> {
    if (OS.CURRENT != OS.Windows || path.name.indexOf('.') >= 0) {
      return listOf(path)
    }

    val extensions = PathEnvironmentVariableUtil.getWindowsExecutableFileExtensions().map {
      path.resolveSibling(path.name + it)
    }

    return listOf(path) + extensions
  }

  private fun validateOnPath(name: String, pathVariableValue: String?): Boolean {
    if (pathVariableValue == null) {
      return false
    }

    if (PathEnvironmentVariableUtil.findInPath(name, pathVariableValue, null) != null) {
      return true
    }

    if (OS.CURRENT != OS.Windows) {
      return false
    }

    for (extension in PathEnvironmentVariableUtil.getWindowsExecutableFileExtensions()) {
      if (PathEnvironmentVariableUtil.findInPath(name + extension, pathVariableValue, null) != null) {
        return true
      }
    }

    return false
  }

  /** Reasons why the configured bazel binary cannot be executed.  */
  enum class Problem {
    NOT_CONFIGURED,
    INVALID_PATH,
    DOES_NOT_EXIST,
    IS_A_DIRECTORY,
    NOT_EXECUTABLE,
  }
}
