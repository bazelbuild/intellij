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

import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.run.state.RunConfigurationCompositeState.RunConfigurationCompositeStateEditor
import com.google.idea.blaze.base.run.state.TestFilterState.TestFilterStateEditor
import com.intellij.openapi.project.Project

/**
 * Composite editor for [BlazeCommandRunConfigurationCommonState] that toggles the test
 * filter field's visibility based on whether the currently selected Bazel command is `test`.
 */
internal class BlazeCommandRunConfigurationCommonStateEditor(
  project: Project,
  states: MutableList<RunConfigurationState>
) : RunConfigurationCompositeStateEditor(project, states) {

  private val testFilterEditor: TestFilterStateEditor?

  init {
    testFilterEditor = findEditor<TestFilterStateEditor>()
    findEditor<BlazeCommandState.BlazeCommandStateEditor>()?.addCommandChangeListener(::updateTestFilterVisibility)
  }

  override fun resetEditorFrom(genericState: RunConfigurationState?) {
    super.resetEditorFrom(genericState)

    if (genericState is BlazeCommandRunConfigurationCommonState) {
      updateTestFilterVisibility(genericState.commandState.command)
    }
  }

  private fun updateTestFilterVisibility(command: BlazeCommandName?) {
    testFilterEditor?.setComponentVisible(BlazeCommandName.TEST == command)
  }

  private inline fun <reified T : RunConfigurationStateEditor> findEditor(): T? {
    return editors.filterIsInstance<T>().firstOrNull()
  }
}
