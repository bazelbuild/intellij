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
@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@file:Suppress("UnstableApiUsage")

package com.google.idea.blaze.clwb.radler

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.sync.SyncListener
import com.google.idea.blaze.base.sync.SyncMode
import com.google.idea.blaze.base.sync.SyncResult
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.util.pluginProjectScope
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID
import com.google.idea.sdkcompat.radler.OCResolveContextSettingsCompat
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ListenableToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButtonModel
import com.intellij.platform.util.coroutines.childScope
import com.jetbrains.cidr.lang.OCFileTypeHelpers
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
import com.jetbrains.cidr.lang.workspace.OCWorkspace
import com.jetbrains.cidr.lang.workspace.OCWorkspaceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class BazelResolveConfigurationWidgetProvider : InspectionWidgetActionProvider {

  override fun createAction(editor: Editor): AnAction? {
    val project = editor.project ?: return null
    if (!Blaze.isBlazeProject(project)) return null

    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    if (!isCppFile(file)) return null

    return BazelConfigSwitchComboAction(project, file)
  }
}


private class BazelConfigSwitchComboAction(
  private val project: Project,
  private val file: VirtualFile,
) : ExpandableComboAction(), Disposable {

  private val scope: CoroutineScope = pluginProjectScope(project).childScope("BlazeCcConfigurationSwitcher")
  private val state: MutableStateFlow<SwitcherState> = MutableStateFlow(SwitcherState.Unknown)

  init {
    val update = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 0)
    val bus = project.messageBus.connect(scope)

    scope.launch {
      update.mapLatest { computeState() }.collect { state.value = it }
    }

    // trigger update on configuration changes
    bus.subscribe(OCWorkspaceListener.TOPIC, object : OCWorkspaceListener {
      override fun selectedResolveConfigurationChanged() {
        update.tryEmit(Unit)
      }
    })

    // trigger update on project data changes
    bus.subscribe(SyncListener.TOPIC, object : SyncListener {
      override fun afterSync(project: Project, context: BlazeContext, syncMode: SyncMode, syncResult: SyncResult, buildIds: ImmutableSet<Int>) {
        update.tryEmit(Unit)
      }
    })

    // trigger initial computation
    update.tryEmit(Unit)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val state = state.value
    val current = state.currentConfig

    if (current == null || state.availableConfigs.size <= 1) {
      e.presentation.isVisible = false
      return
    }

    e.presentation.text = current.name
    e.presentation.icon = AllIcons.General.Settings
    e.presentation.isVisible = true
  }

  private fun createPopupActionGroup(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()
    val state = state.value

    for (config in state.availableConfigs) {
      actionGroup.add(
        SwitchContextAction(
          config = config,
          isCurrent = config == state.currentConfig,
        )
      )
    }

    return actionGroup
  }

  override fun createToolbarComboButton(model: ToolbarComboButtonModel): ToolbarComboButton = SwitcherComboBox(model)

  override fun createPopup(event: AnActionEvent): JBPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup(
      /* title = */ null,
      /* actionGroup = */ createPopupActionGroup(),
      /* dataContext = */ event.dataContext,
      /* aid = */ JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      /* showDisabledActions = */ true,
      /* disposeCallback = */ null,
      /* maxRowCount = */ -1,
      /* preselectCondition = */ { action -> action is SwitchContextAction && action.isCurrent },
      /* actionPlace = */ null,
    )
  }

  override fun dispose() {
    scope.cancel()
  }

  private inner class SwitcherComboBox(model: ToolbarComboButtonModel) : ListenableToolbarComboButton(model) {

    override fun installListeners(project: Project?, disposable: Disposable) {
      state.debounce(100.milliseconds)
        .onEach { updateWidgetAction() }
        .launchIn(scope.childScope("ui scope", Dispatchers.EDT))
    }
  }

  private inner class SwitchContextAction(
    private val config: SwitcherConfig,
    val isCurrent: Boolean,
  ) : AnAction(config.name) {

    override fun actionPerformed(e: AnActionEvent) {
      OCResolveContextSettingsCompat.setSelectedConfiguration(project, config.ocResolveConfig)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private fun toSwitcherConfig(data: BlazeProjectData, config: OCResolveConfiguration): SwitcherConfig? {
    val id = BlazeResolveConfigurationID.fromOCResolveConfiguration(config) ?: return null
    val configuration = data.configurationData().get(id.configurationId) ?: return null
    val name = "${configuration.mnemonic()} - ${configuration.cpu()} (${id.configurationId})"

    return SwitcherConfig(name, id.configurationId, config)
  }

  private fun findSelectedConfiguration(configurations: List<SwitcherConfig>): SwitcherConfig {
    val mapping = configurations.associateBy { it.ocResolveConfig }
    val priorityConfig = OCResolveContextSettingsCompat.findPriorityConfiguration(project, mapping.keys)

    return if (priorityConfig != null) {
      mapping.getValue(priorityConfig)
    } else {
      configurations[0]
    }
  }

  private fun computeState(): SwitcherState {
    val projectData = BlazeProjectDataManager.getInstance(project).blazeProjectData ?: return SwitcherState.Unknown

    val configurations = OCWorkspace.getInstance(project)
      .getConfigurationsForFile(file)
      .mapNotNull { toSwitcherConfig(projectData, it) }

    if (configurations.isEmpty()) {
      return SwitcherState.Unknown
    }

    return SwitcherState.Loaded(
      currentConfig = findSelectedConfiguration(configurations),
      availableConfigs = configurations,
    )
  }
}

private data class SwitcherConfig(
  val name: String,
  val configurationId: String,
  val ocResolveConfig: OCResolveConfiguration,
)

private sealed interface SwitcherState {
  val currentConfig: SwitcherConfig?
  val availableConfigs: List<SwitcherConfig>

  object Unknown : SwitcherState {
    override val currentConfig: SwitcherConfig? get() = null
    override val availableConfigs: List<SwitcherConfig> get() = emptyList()
  }

  data class Loaded(
    override val currentConfig: SwitcherConfig,
    override val availableConfigs: List<SwitcherConfig>,
  ) : SwitcherState
}

private fun isCppFile(file: VirtualFile): Boolean {
  val name = file.name
  return OCFileTypeHelpers.isSourceFile(name) || OCFileTypeHelpers.isHeaderFile(name)
}

