/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.sync.data.BlazeDataStorage
import com.google.idea.blaze.base.wizard2.BazelImportCurrentProjectAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.cidr.lang.daemon.OCFileScopeProvider.Companion.getProjectSourceLocationKind
import com.google.idea.sdkcompat.clion.projectStatus.*
import com.jetbrains.cidr.project.workspace.CidrWorkspace
import java.io.File

/// This function is a little overloaded, ensures the project could be imported
// and returns the potential workspace root.
@RequiresReadLock
private fun guessWorkspaceRoot(project: Project, file: VirtualFile?): String? {
  if (file == null || !file.isValid) {
    return null
  }

  if (!Blaze.isBlazeProject(project)) {
    return null
  }

  if (!isProjectAwareFile(file, project) && file.fileType != BuildFileType.INSTANCE) {
    return null
  }

  if (getProjectSourceLocationKind(project, file).isInProject()) {
    return null
  }

  val workspaceRoot = project.basePath ?: return null

  // if somehow the project was open in .clwb but is not imported as a blaze project
  return workspaceRoot.removeSuffix(BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY)
}

@RequiresReadLock
private fun guessWidgetStatus(project: Project, currentFile: VirtualFile?): WidgetStatus? {
  if (Blaze.isBlazeProject(project)) {
    return DefaultWidgetStatus(Status.OK, Scope.Project, "Bazel project is configured")
  }

  if (currentFile == null || guessWorkspaceRoot(project, currentFile) == null) {
    return null
  }

  val status = if (CidrWorkspace.getInitializedWorkspaces(project).isEmpty()) {
    Status.Warning
  } else {
    Status.Info
  }

  return DefaultWidgetStatus(status, Scope.Project, "Bazel project is not configured")
}

class BazelProjectFixesProvider : ProjectFixesProvider {

  override suspend fun collectFixes(project: Project, file: VirtualFile?, context: DataContext): List<AnAction> {
    val workspaceRoot = readAction { guessWorkspaceRoot(project, file) } ?: return emptyList()
    return listOf(BazelImportCurrentProjectAction(File(workspaceRoot)))
  }
}

class BazelWidgetStatusProvider : WidgetStatusProvider {

  override suspend fun computeWidgetStatus(project: Project, currentFile: VirtualFile?): WidgetStatus? {
    return readAction { guessWidgetStatus(project, currentFile) }
  }
}

class BazelEditorNotificationProvider : EditorNotificationWarningProvider {

  override suspend fun computeProjectNotification(project: Project, file: VirtualFile): ProjectNotification? {
    return convertStatus(readAction { guessWidgetStatus(project, file) })
  }
}
