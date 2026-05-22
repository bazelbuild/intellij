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
package com.google.idea.blaze.base.run.state

import com.google.idea.blaze.base.projectview.ProjectViewEdit
import com.google.idea.blaze.base.projectview.section.ListSection
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.settings.ui.OpenProjectViewAction
import com.google.idea.blaze.base.sync.BlazeSyncManager
import com.intellij.execution.RunManager
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages

private const val SHOWN_KEY = "legacy_blaze_flags_migration_shown"
private const val SYNC_REASON = "LegacyBlazeFlagsMigrationNotifier"

/**
 * Once per project, scans for run configurations carrying legacy `<blaze-user-flag>` entries that
 * no longer have a typed home and offers to move them into the project view's `build_flags:`
 * section via a modal dialog.
 */
class LegacyBlazeFlagsMigrationNotifier : StartupActivity.DumbAware {

  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    if (isLegacyMigrationShown(project)) return

    val affected = collectLegacyConfigurations(project)
    if (affected.isEmpty()) return

    ApplicationManager.getApplication().invokeLater {
      markLegacyMigrationShown(project)

      val exitCode = showLegacyBlazeFlagsMigrationDialog(project, affected)
      if (exitCode != DialogWrapper.OK_EXIT_CODE) return@invokeLater

      migrateToProjectView(project, affected)
    }
  }
}

private fun collectLegacyConfigurations(project: Project): Map<String, List<String>> {
  return RunManager.getInstance(project).allConfigurationsList
    .filterIsInstance<BlazeCommandRunConfiguration>()
    .mapNotNull { config ->
      config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)?.let { state ->
        if (state.legacyUserFlags.isEmpty()) null else config.name to state.legacyUserFlags
      }
    }
    .toMap()
}

private fun migrateToProjectView(project: Project, affected: Map<String, List<String>>) {
  val deduped = affected.values.flatten().distinct()

  val edit = ProjectViewEdit.editLocalProjectView(project) { builder ->
    val existing = builder.getLast(BuildFlagsSection.KEY)
    val existingFlags = existing?.items()?.toSet() ?: emptySet()
    val updated = ListSection.update(BuildFlagsSection.KEY, existing).apply {
      deduped.filter { it !in existingFlags }.forEach(::add)
    }
    builder.replace(existing, updated)
    true
  }

  if (edit == null) {
    Messages.showErrorDialog(
      project,
      "Could not modify the project view. Check for errors and try again.",
      "Migration Failed",
    )
  } else {
    edit.apply()
    clearLegacyFlagsFromConfigs(project)
    OpenProjectViewAction.openLocalProjectViewFile(project)
    BlazeSyncManager.getInstance(project).incrementalProjectSync(SYNC_REASON)
  }

}

private fun clearLegacyFlagsFromConfigs(project: Project) {
  val runManager = RunManagerImpl.getInstanceImpl(project)
  runManager.allSettings.forEach { settings ->
    val config = settings.configuration as? BlazeCommandRunConfiguration ?: return@forEach
    val state = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java)
      ?: return@forEach
    if (state.legacyUserFlags.isEmpty()) return@forEach

    state.clearLegacyUserFlags()
    runManager.fireRunConfigurationChanged(settings)
  }
}

private fun isLegacyMigrationShown(project: Project): Boolean {
  return PropertiesComponent.getInstance(project).getBoolean(SHOWN_KEY, false)
}

private fun markLegacyMigrationShown(project: Project) {
  PropertiesComponent.getInstance(project).setValue(SHOWN_KEY, true)
}
