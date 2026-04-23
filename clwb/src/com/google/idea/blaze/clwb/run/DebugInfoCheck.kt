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
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.cidr.lang.CLanguageKind
import com.jetbrains.cidr.lang.workspace.OCWorkspace
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind

private const val NOTIFICATION_TITLE = "Debug Info Warning"

private val NOTIFICATION_MESSAGE = """
  CLion detected that your target might not be debuggable.
  Target: %s
  Configuration: %s
""".trimIndent()

private enum class NotificationAction(val text: String) {
  CONTINUE("Continue"),
  CANCEL("Cancel"),
  DISMISS_FOR_TARGET("Dismiss for Target"),
  DISMISS_FOR_PROJECT("Dismiss for Project"),
}

class DebugInfoCheck(
  private val project: Project,
  private val configurations: DiscoverTargetConfigurations.Output,
) : BuildStep<Unit> {

  override val title: String = "Check CC Debug Info"

  override fun run(ctx: BlazeContext) {
    val target = configurations.mainTarget

    val compilerKind = findCompilerKind(project, configurations.mainConfiguration.checksum)

    val nonDebuggableTargets = configurations.compileActions.asSequence()
      .filter { (_, action) -> !checkDebugInfoPresent(action.arguments, compilerKind) }
      .toList()

    if (nonDebuggableTargets.isEmpty()) return

    val warning = StringBuilder("Non-debuggable dependencies:\n")
    nonDebuggableTargets.joinTo(warning, "\n") { (label, action) -> "$label: ${action.arguments.joinToString(" ")}" }

    if (DebugInfoDismissalState.isDismissed(project, target)) return

    val result = invokeAndWaitIfNeeded {
      Messages.showDialog(
        /* project = */ project,
        /* message = */ NOTIFICATION_MESSAGE.format(target, configurations.mainConfiguration),
        /* title = */ NOTIFICATION_TITLE,
        /* options = */ NotificationAction.entries.map { it.text }.toTypedArray(),
        /* defaultOptionIndex = */ NotificationAction.CANCEL.ordinal,
        /* icon = */ Messages.getWarningIcon(),
      )
    }

    when (result) {
      NotificationAction.CANCEL.ordinal -> fail(warning.toString())
      NotificationAction.DISMISS_FOR_TARGET.ordinal -> DebugInfoDismissalState.dismissForTarget(project, target)
      NotificationAction.DISMISS_FOR_PROJECT.ordinal -> DebugInfoDismissalState.dismissForProject(project)
    }

    warn(ctx, warning.toString())
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
