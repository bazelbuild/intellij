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
package com.google.idea.blaze.base.projectview

import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleTextAttributes

/** Appends a "(current)" label to the project view file the project is currently using.  */
class ProjectViewNodeDecorator : ProjectViewNodeDecorator {

  override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
    val project = node.project
    if (!Blaze.isBlazeProject(project)) return

    val file = node.virtualFile
    if (file == null || file.isDirectory || !ProjectViewStorageManager.isProjectViewFile(file.name)) return

    val activePath = BlazeImportSettingsManager.getInstance(project).getImportSettings()?.projectViewFile
    if (activePath == null || !FileUtil.pathsEqual(file.path, activePath)) return

    data.clearText()
    data.addText(file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    data.addText(" (current)", SimpleTextAttributes.GRAY_ATTRIBUTES)
  }
}
