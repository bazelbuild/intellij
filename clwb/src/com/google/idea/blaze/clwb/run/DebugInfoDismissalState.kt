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
package com.google.idea.blaze.clwb.run

import com.google.idea.blaze.base.model.primitives.Label
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "DebugInfoDismissalState")
class DebugInfoDismissalState : PersistentStateComponent<DebugInfoDismissalState> {

  var dismissedForProject: Boolean = false
  var dismissedTargets: MutableList<String> = mutableListOf()

  override fun getState(): DebugInfoDismissalState = this

  override fun loadState(state: DebugInfoDismissalState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {

    private fun getInstance(project: Project): DebugInfoDismissalState {
      return project.getService(DebugInfoDismissalState::class.java)
    }

    fun isDismissed(project: Project, target: Label): Boolean {
      val state = getInstance(project)
      return state.dismissedForProject || state.dismissedTargets.contains(target.toString())
    }

    fun dismissForTarget(project: Project, target: Label) {
      getInstance(project).dismissedTargets.add(target.toString())
    }

    fun dismissForProject(project: Project) {
      getInstance(project).dismissedForProject = true
    }
  }
}
