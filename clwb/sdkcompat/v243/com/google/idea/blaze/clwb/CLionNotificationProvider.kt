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
import com.google.idea.blaze.base.wizard2.BazelImportCurrentProjectAction
import com.google.idea.blaze.base.wizard2.BazelNotificationProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.jetbrains.cidr.project.ui.convertStatus
import com.jetbrains.cidr.project.ui.isProjectAwareFile
import com.jetbrains.cidr.project.ui.notifications.EditorNotificationWarningProvider
import com.jetbrains.cidr.project.ui.notifications.ProjectNotification
import com.jetbrains.cidr.project.ui.popup.ProjectFixesProvider
import com.jetbrains.cidr.project.ui.widget.*

// #api241
class CLionNotificationProvider : ProjectFixesProvider, WidgetStatusProvider, EditorNotificationWarningProvider {
  override suspend fun collectFixes(project: Project, file: VirtualFile?, dataContext: DataContext): List<AnAction> {
    if (file == null) {
      return emptyList()
    }

    if (Blaze.isBlazeProject(project)) {
      return emptyList()
    }

    if (!isProjectAwareFile(file, project) && file.fileType != BuildFileType.INSTANCE) {
      return emptyList()
    }
    if (!BazelImportCurrentProjectAction.projectCouldBeImported(project)) {
      return emptyList()
    }

    return listOf(ImportBazelAction(project.basePath ?: return emptyList()))
  }

  private class ImportBazelAction(private val root: String) : AnAction("Import Bazel project") {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
      BazelImportCurrentProjectAction.createAction(root).run()
    }
  }

  override fun getProjectNotification(project: Project, virtualFile: VirtualFile): ProjectNotification? {
    return convertStatus(getWidgetStatus(project, virtualFile))
  }

  override fun getWidgetStatus(project: Project, file: VirtualFile?): WidgetStatus? {
     if (Blaze.isBlazeProject(project)) {
       return DefaultWidgetStatus(Status.OK, Scope.Project, "Project is configured")
     }

     if (file == null) {
       return null
     }

     if (!isProjectAwareFile(file, project) && file.getFileType() !== BuildFileType.INSTANCE) {
       return null
     }
     if (!BazelImportCurrentProjectAction.projectCouldBeImported(project)) {
       return null
     }

     if (project.basePath == null) {
       return null
     }

    return DefaultWidgetStatus(Status.Warning, Scope.Project, "Project is not configured")
  }

  companion object {
    private fun unregisterGenericProvider(project: Project) {
      val extensionPoint = EditorNotificationProvider.EP_NAME.getPoint(project)

      // Note: We need to remove the default style of showing project status and fixes used in
      // Android Studio and IDEA to introduce CLion's PSW style.
      for (extension in extensionPoint.extensions) {
        if (extension is BazelNotificationProvider) {
          extensionPoint.unregisterExtension(extension)
        }
      }
    }

    @JvmStatic
    fun register(project: Project) {
      unregisterGenericProvider(project)

      val projectFixes = ProjectFixesProvider.Companion.EP_NAME.point
      projectFixes.registerExtension(CLionNotificationProvider())

      val projectNotifications = EditorNotificationWarningProvider.Companion.EP_NAME.point
      projectNotifications.registerExtension(CLionNotificationProvider())

      val widgetStatus = WidgetStatusProvider.Companion.EP_NAME.point
      widgetStatus.registerExtension(CLionNotificationProvider())
    }
  }
}
