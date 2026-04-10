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

package com.google.idea.blaze.clwb.radler

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.sync.SyncListener
import com.google.idea.blaze.base.sync.SyncMode
import com.google.idea.blaze.base.sync.SyncResult
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID
import com.google.idea.sdkcompat.radler.OCResolveContextSettingsCompat
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarComboButtonModel
import com.intellij.util.ui.UIUtil
import com.jetbrains.cidr.lang.OCFileTypeHelpers
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurations
import com.jetbrains.cidr.lang.workspace.OCWorkspace
import com.jetbrains.cidr.lang.workspace.OCWorkspaceListener
import org.jetbrains.letsPlot.commons.intern.filterNotNullValues

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
) : ExpandableComboAction(), Disposable.Default {

  private val state: MemoizedProperty<SwitcherState?> = MemoizedProperty { computeState() }
  private var comboBox: ToolbarComboButton? = null

  init {
    val bus = project.messageBus.connect(this)

    // trigger update on configuration changes
    bus.subscribe(OCWorkspaceListener.TOPIC, object : OCWorkspaceListener {
      override fun selectedResolveConfigurationChanged() {
        state.invalidate()
      }

      override fun workspaceChanged(event: OCWorkspaceListener.OCWorkspaceEvent) {
        state.invalidate()
      }
    })

    // trigger update on project data changes
    bus.subscribe(SyncListener.TOPIC, object : SyncListener {
      override fun afterSync(project: Project, context: BlazeContext, syncMode: SyncMode, syncResult: SyncResult, buildIds: ImmutableSet<Int>) {
        state.invalidate()
      }
    })
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val state = this.state.get()

    if (state == null || state.availableConfigs.size <= 1) {
      e.presentation.isVisible = false
      return
    }

    val autoPrefix = if (state.isAutoSelect) "Auto: " else ""

    e.presentation.text = autoPrefix + state.currentConfig.name
    e.presentation.icon = AllIcons.General.Settings
    e.presentation.isVisible = true

    ActionToolbar.findToolbarBy(comboBox)?.let { toolbar ->
      UIUtil.invokeLaterIfNeeded {
        toolbar.updateActionsAsync()
      }
    }
  }

  private fun createPopupActionGroup(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()
    val state = this.state.get() ?: return actionGroup

    actionGroup.add(AutoSelectAction(state.isAutoSelect))
    actionGroup.add(Separator.create())

    for (config in state.availableConfigs) {
      actionGroup.add(
        SwitchContextAction(
          config = config,
          isCurrent = !state.isAutoSelect && config == state.currentConfig,
        )
      )
    }

    return actionGroup
  }

  override fun createToolbarComboButton(model: ToolbarComboButtonModel): ToolbarComboButton {
    return ToolbarComboButton(model).also { comboBox = it }
  }

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

  private inner class SwitchContextAction(
    private val config: SwitcherConfig,
    val isCurrent: Boolean,
  ) : AnAction(config.name) {

    override fun actionPerformed(e: AnActionEvent) {
      OCResolveContextSettingsCompat.setSelectedConfiguration(project, config.ocResolveConfig)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = if (isCurrent) AllIcons.Actions.Checked else null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }

  private inner class AutoSelectAction(val isCurrent: Boolean) : AnAction("Auto") {

    override fun actionPerformed(e: AnActionEvent) {
      OCResolveContextSettingsCompat.resetConfigurationPriorities(project)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.icon = if (isCurrent) AllIcons.Actions.Checked else null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }


  private fun toSwitcherConfig(data: BlazeProjectData, config: OCResolveConfiguration): SwitcherConfig? {
    val id = BlazeResolveConfigurationID.fromOCResolveConfiguration(config) ?: return null
    val configuration = data.configurationData().get(id.configurationId) ?: return null
    val name = "${configuration.platformName()} (${id.configurationId})"

    return SwitcherConfig(name, id.configurationId, config)
  }

  private fun computeState(): SwitcherState? {
    val projectData = BlazeProjectDataManager.getInstance(project).blazeProjectData ?: return null

    val configurations = OCWorkspace.getInstance(project)
      .getConfigurationsForFile(file)
      .associateWith { toSwitcherConfig(projectData, it) }
      .filterNotNullValues()

    if (configurations.isEmpty()) return null
    val selected = OCResolveConfigurations.findPreselectedOrSuitableConfiguration(project, configurations.keys)

    return SwitcherState(
      currentConfig = configurations.getValue(selected.first),
      availableConfigs = configurations.values.toList(),
      isAutoSelect = !selected.second,
    )
  }
}

private data class SwitcherConfig(
  val name: String,
  val configurationId: String,
  val ocResolveConfig: OCResolveConfiguration,
)

private data class SwitcherState(
  val currentConfig: SwitcherConfig,
  val availableConfigs: List<SwitcherConfig>,
  val isAutoSelect: Boolean,
)

private fun isCppFile(file: VirtualFile): Boolean {
  return OCFileTypeHelpers.isSourceFile(file.name) ||
      OCFileTypeHelpers.isHeaderFile(file.name) ||
      file.extension.isNullOrBlank() // headers without an extension
}

private class MemoizedProperty<T>(private val compute: () -> T) {

  companion object {
    private val INVALID = Any()
  }

  private var value: Any? = INVALID

  @Synchronized
  fun get(): T {
    if (value == INVALID) {
      value = compute()
    }

    @Suppress("UNCHECKED_CAST")
    return value as T
  }

  @Synchronized
  fun invalidate() {
    value = INVALID
  }
}
