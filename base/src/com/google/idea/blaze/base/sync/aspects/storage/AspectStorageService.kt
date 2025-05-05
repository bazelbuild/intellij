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
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.model.BlazeVersionData
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.Scope
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.sync.SyncProjectState
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException
import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.toolwindow.Task
import com.google.idea.blaze.common.Label
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val ASPECT_TASK_TITLE = "Write Aspects"
private const val ASPECT_DIRECTORY = "aspects"

private const val BAZEL_IGNORE_FILE = ".bazelignore"

private val LOG = logger<AspectStorageService>()

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
  fun prepare(parentCtx: BlazeContext?, state: SyncProjectState?) {
    val parentScope = parentCtx?.getScope(ToolWindowScope::class.java)

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

      detectBazelIgnoreAndEmitWarning(ctx, directory)

      try {
        if (!Files.exists(directory)) {
          Files.createDirectories(directory)
        }
      } catch (e: IOException) {
        throw SyncFailedException("Could not create aspect directory", e)
      }

      for (writer in AspectWriter.EP_NAME.extensionList) {
        try {
          if (state == null) {
            writer.writeDumb(directory, project)
            ctx.println("Aspects written (dumb): ${writer.name()}")
          } else {
            writer.write(directory, project, state)
            ctx.println("Aspects written: ${writer.name()}")
          }
        } catch (e: SyncFailedException) {
          throw SyncFailedException("Could not writer aspects: ${writer.name()}", e)
        }
      }
    }
  }

  /**
   * Convince wrapper that derives an appropriate [SyncProjectState] for [prepare].
   */
  @Throws(SyncFailedException::class)
  fun prepare(parentCtx: BlazeContext?, projectData: BlazeProjectData, versionData: BlazeVersionData) {
    val state = SyncProjectState.builder()
      .setProjectViewSet(ProjectViewSet.EMPTY) // not used by any AspectWriter
      .setLanguageSettings(projectData.workspaceLanguageSettings)
      .setExternalWorkspaceData(projectData.externalWorkspaceData)
      .setWorkspacePathResolver(projectData.workspacePathResolver)
      .setWorkingSet(null)
      .setBlazeVersionData(versionData)
      .setBlazeInfo(projectData.blazeInfo)
      .build()

    prepare(parentCtx, state)
  }

  fun resolve(file: String): Optional<Label> {
    val settings = BlazeImportSettingsManager.getInstance(project).importSettings ?: return Optional.empty()
    val directory = aspectDirectory(settings) ?: return Optional.empty()

    val relativePath = directory.resolve(file)
    if (!Files.exists(relativePath)) return Optional.empty()

    val absolutePath = Path.of(settings.workspaceRoot).relativize(relativePath)
    return Optional.of(Label.fromWorkspacePackageAndName("", absolutePath.parent, absolutePath.fileName))
  }

  private fun aspectDirectory(settings: BlazeImportSettings): Path? {
    val projectPath = project.basePath?.let(Path::of) ?: return null
    val workspacePath = settings.workspaceRoot.takeIf(String::isNotBlank)?.let(Path::of) ?: return null

    // if the project data path is contained in the workspace, the aspects can be placed in there
    if (projectPath.startsWith(workspacePath) || ApplicationManager.getApplication().isUnitTestMode) {
      return projectPath.resolve(ASPECT_DIRECTORY)
    }

    // if this is not the case, fallback to .ijwb_aspects or .clwb_aspects
    return workspacePath.resolve(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY + "_aspects")
  }

  /**
   * The aspects should not be in the bazel ignore file. Emit a warning if any subpath of the aspect directory is
   * mentioned in the bazel ignore file.
   */
  private fun detectBazelIgnoreAndEmitWarning(ctx: BlazeContext, aspectPath: Path) {
    val projectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() ?: return

    val bazelIgnore = projectData.workspacePathResolver.resolveToFile(BAZEL_IGNORE_FILE).toPath()
    if (!Files.exists(bazelIgnore)) return

    val aspectFile = aspectPath.toFile()
    val aspectRelativePath = projectData.workspacePathResolver.getWorkspacePath(aspectFile)?.asPath() ?: return

    val lines = try {
      Files.lines(bazelIgnore).map(String::trim).toList()
    } catch (e: IOException) {
      LOG.warn("could not read bazel ignore file", e)
      return
    }

    for (i in 1..aspectRelativePath.nameCount) {
      if (!lines.contains(aspectRelativePath.subpath(0, i).toString())) continue

      IssueOutput.warn("Aspects in $BAZEL_IGNORE_FILE file")
        .withDescription("Please make sure that $aspectRelativePath is not covered by the $BAZEL_IGNORE_FILE file")
        .withFile(bazelIgnore.toFile())
        .submit(ctx)

      break
    }
  }
}
