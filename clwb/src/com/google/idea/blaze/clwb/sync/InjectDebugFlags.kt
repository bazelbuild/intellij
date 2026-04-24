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
package com.google.idea.blaze.clwb.sync

import com.google.idea.blaze.base.projectview.ProjectViewEdit
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.section.ScalarSection
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/** Reads the `inject_debug_flags` project-view section. Defaults to `false`. */
fun shouldInjectDebugFlags(project: Project): Boolean {
  val projectViewSet = ProjectViewManager.getInstance(project).projectViewSet ?: return false
  return projectViewSet.getScalarValue(InjectDebugFlagsSection.KEY).orElse(false)
}

/**
 * Writes `inject_debug_flags: true` into the project's local `.bazelproject`.
 *
 * Switches to the EDT because [ProjectViewEdit.apply] shows a modal "Updating VFS"
 * progress dialog.
 */
fun enableInjectDebugFlags(project: Project) {
  ApplicationManager.getApplication().invokeAndWait {
    ProjectViewEdit.editLocalProjectView(project) { builder ->
      val existing = builder.getLast(InjectDebugFlagsSection.KEY)
      builder.replace(existing, ScalarSection.builder(InjectDebugFlagsSection.KEY).set(true))
      true
    }?.apply()
  }
}
