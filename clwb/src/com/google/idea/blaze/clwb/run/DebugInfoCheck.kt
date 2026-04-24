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

import com.google.idea.blaze.base.buildview.BuildStep
import com.google.idea.blaze.base.buildview.fail
import com.google.idea.blaze.base.buildview.warn
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.clwb.sync.enableInjectDebugFlags
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID
import com.google.idea.common.aquery.ActionGraph
import com.intellij.execution.RunManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.cidr.lang.CLanguageKind
import com.jetbrains.cidr.lang.workspace.OCWorkspace
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val NOTIFICATION_TITLE = "Debug Info Warning"

private const val INJECT_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE
private const val DISMISS_TARGET_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 1
private const val DISMISS_PROJECT_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 2

class DebugInfoCheck(
  private val env: ExecutionEnvironment,
  private val configurations: DiscoverTargetConfigurations.Output,
) : BuildStep<Unit> {

  override val title: String = "Check CC Debug Info"

  override fun run(ctx: BlazeContext) {
    val project = env.project
    val target = configurations.mainTarget

    val compilerKind = findCompilerKind(project, configurations.mainConfiguration.checksum)

    val nonDebuggableTargets = configurations.compileActions.asSequence()
      .filter { (_, action) -> !checkDebugInfoPresent(action.arguments, compilerKind) }
      .toList()

    if (nonDebuggableTargets.isEmpty()) return

    val warning = StringBuilder("Non-debuggable dependencies:\n")
    nonDebuggableTargets.joinTo(warning, "\n") { (label, action) -> "$label: ${action.arguments.joinToString(" ")}" }

    if (DebugInfoDismissalState.isDismissed(project, target)) return

    var exitCode = DialogWrapper.CANCEL_EXIT_CODE
    ApplicationManager.getApplication().invokeAndWait {
      val dialog = DebugInfoDialog(project, target, configurations.mainConfiguration)
      dialog.show()
      exitCode = dialog.exitCode
    }

    when (exitCode) {
      DialogWrapper.OK_EXIT_CODE -> {} // Continue — fall through to the warning below.
      DialogWrapper.CANCEL_EXIT_CODE -> fail(warning.toString())
      INJECT_EXIT_CODE -> {
        enableInjectDebugFlags(project)
        rerunRunConfiguration(env)
        fail("Debug flag injection enabled, re-running debug session.")
      }

      DISMISS_TARGET_EXIT_CODE -> DebugInfoDismissalState.dismissForTarget(project, target)
      DISMISS_PROJECT_EXIT_CODE -> DebugInfoDismissalState.dismissForProject(project)
    }

    warn(ctx, warning.toString())
  }
}

private class DebugInfoDialog(
  project: Project,
  private val target: Label,
  private val configuration: ActionGraph.Configuration,
) : DialogWrapper(project) {

  init {
    title = NOTIFICATION_TITLE
    setOKButtonText("Continue")
    setButtonsAlignment(SwingConstants.CENTER)
    init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row { label("CLion detected that your target might not be debuggable.") }
    indent {
      row { text("Target: $target<br>Configuration: $configuration") }
    }
    separator()
    row {
      text(
        "The target was built without debug info. You can " +
            "<a>inject debug flags</a> to have the plugin build " +
            "C/C++ run configurations in debug mode."
      ) { close(INJECT_EXIT_CODE, true) }
    }
    row {
      comment(
        "Note: toggling this invalidates Bazel's analysis cache for the next build, " +
            "since the build configuration changes."
      )
    }
    separator()
  }

  override fun createActions(): Array<Action> = arrayOf(
    okAction,
    cancelAction,
    DialogWrapperExitAction("Dismiss for Target", DISMISS_TARGET_EXIT_CODE),
    DialogWrapperExitAction("Dismiss for Project", DISMISS_PROJECT_EXIT_CODE),
  )
}

/**
 * Re-queues [env]'s run configuration via the normal execution infrastructure. Intended to be
 * called right before aborting the current [DebugInfoCheck] with [fail] so the next attempt picks
 * up the updated project view.
 */
private fun rerunRunConfiguration(env: ExecutionEnvironment) {
  val config = env.runProfile as? BlazeCommandRunConfiguration ?: return
  val settings = RunManager.getInstance(env.project).findSettings(config) ?: return

  ApplicationManager.getApplication().invokeAndWait {
    ExecutionUtil.runConfiguration(settings, env.executor)
  }
}

/**
 * Finds the [OCCompilerKind] for a given Bazel configuration by matching the
 * configuration checksum against OCResolveConfigurations in the workspace.
 */
private fun findCompilerKind(project: Project, bazelConfigHash: String): OCCompilerKind? {
  for (config in OCWorkspace.getInstance(project).configurations) {
    val id = BlazeResolveConfigurationID.fromOCResolveConfiguration(config) ?: continue

    if (bazelConfigHash.startsWith(id.configurationId)) {
      return config.getCompilerSettings(CLanguageKind.CPP).compilerKind
    }
  }

  return null
}

/**
 * Checks that the given compile action arguments contain debug info flags
 * appropriate for the given compiler kind.
 */
fun checkDebugInfoPresent(arguments: List<String>, compilerKind: OCCompilerKind?): Boolean {
  return when (compilerKind) {
    GCCCompilerKind, ClangCompilerKind -> hasGccDebugInfo(arguments)
    MSVCCompilerKind -> hasMsvcDebugInfo(arguments)
    ClangClCompilerKind -> hasGccDebugInfo(arguments) || hasClangClDebugInfo(arguments) || hasMsvcDebugInfo(arguments)
    else -> hasGccDebugInfo(arguments) || hasMsvcDebugInfo(arguments)
  }
}

private fun hasGccDebugInfo(arguments: List<String>): Boolean {
  val last = arguments.lastOrNull { it.startsWith("-g") } ?: return false
  return last != "-g0"
}

private fun hasClangClDebugInfo(arguments: List<String>): Boolean {
  val last = arguments.lastOrNull { it.startsWith("/clang:-g") } ?: return false
  return last != "/clang:-g0"
}

private fun hasMsvcDebugInfo(arguments: List<String>): Boolean {
  return arguments.any { it.startsWith("/Z") }
}
