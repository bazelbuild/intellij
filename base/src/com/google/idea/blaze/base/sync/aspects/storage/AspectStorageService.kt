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

import com.google.idea.blaze.base.buildview.println
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException
import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.idea.blaze.base.toolwindow.Task
import com.google.idea.blaze.common.Label
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val ASPECT_TASK_TITLE = "Write Aspects"
private const val ASPECT_DIRECTORY = "aspects"

@Service(Service.Level.PROJECT)
class AspectStorageService(private val project: Project, private val scope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun of(project: Project): AspectStorageService = project.service()
  }

  /**
   * Copies all bundled aspects to a workspace relative directory.
   * This should be called as one of the first steps in the sync workflow.
   *
   * Register a [AspectWriter] to provide aspect files.
   */
  @Throws(SyncFailedException::class)
  fun prepare(parentCtx: BlazeContext) {
    val parentScope = parentCtx.getScope(ToolWindowScope::class.java)

    Scope.push(parentCtx) { ctx ->

      // if there is no parent ToolWindowScope, the output is not supposed to be printed
      if (parentScope != null) {
        val scope = ToolWindowScope.Builder(project, Task(project, ASPECT_TASK_TITLE, Task.Type.SYNC, parentScope.task))
        ctx.push(scope.build())
      }

      val settings = BlazeImportSettingsManager.getInstance(project).importSettings
        ?: throw SyncFailedException("No import settings found")

      val directory = aspectDirectory(settings)
        ?: throw SyncFailedException("Could not determine aspect directory")

      ctx.println("Writing aspects to $directory")

      try {
        if (!Files.exists(directory)) {
          Files.createDirectories(directory)
        }
      } catch (e: IOException) {
        throw SyncFailedException("Could not create aspect directory", e)
      }

      for (writer in AspectWriter.EP_NAME.extensionList) {
        try {
          writer.write(directory, project)
          ctx.println("Aspects written: ${writer.name()}")
        } catch (e: SyncFailedException) {
          throw SyncFailedException("Could not writer aspects: ${writer.name()}", e)
        }
      }
    }
  }

  fun resolve(file: String): Optional<Label> {
    val settings = BlazeImportSettingsManager.getInstance(project).importSettings ?: return Optional.empty()
    val directory = aspectDirectory(settings) ?: return Optional.empty()

    val file = directory.resolve(file)
    if (!Files.exists(file)) return Optional.empty()

    val path = Path.of(settings.workspaceRoot).relativize(file)
    return Optional.of(Label.fromWorkspacePackageAndName("", path.parent, path.fileName))
  }

  private fun aspectDirectory(settings: BlazeImportSettings): Path? {
    val projectPath = project.basePath?.let(Path::of) ?: return null
    val workspacePath = settings.workspaceRoot.takeIf(String::isNotBlank)?.let(Path::of) ?: return null

    // if the project data path is contained in the workspace, the aspects can be placed in there
    if (projectPath.startsWith(workspacePath)) {
      return projectPath.resolve(ASPECT_DIRECTORY)
    }

    // if this is not the case, fallback to .ijwb_aspects or .clwb_aspects
    return workspacePath.resolve(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY + "_aspects")
  }
}
