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
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.clwb.sync.enableInjectDebugFlags
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID
import com.google.idea.common.aquery.ActionGraph
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.system.OS
import com.jetbrains.cidr.lang.CLanguageKind
import com.jetbrains.cidr.lang.workspace.OCWorkspace
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind

class DebugInfoCheck(
  private val env: ExecutionEnvironment,
  private val configurations: DiscoverTargetConfigurations.Output,
) : BuildStep<Unit> {

  override val title: String = "Check CC Debug Info"

  override fun run(ctx: BlazeContext) {
    val project = env.project
    val target = configurations.mainTarget

    val compilerKind = findCompilerKind(project, configurations.mainConfiguration.checksum)

    val flaggedCompileActions = configurations.compileActions.asSequence()
      .filter { (_, action) -> !checkCompileAction(action.arguments, compilerKind) }
      .map { (label, action) -> label to action }
    val flaggedLinkActions = configurations.linkActions.asSequence()
      .filter { (_, action) -> !checkLinkAction(action.arguments, compilerKind) }
      .map { (label, action) -> label to action }

    val flaggedActions = (flaggedCompileActions + flaggedLinkActions).toList()
    if (flaggedActions.isEmpty()) return

    val warning = StringBuilder("Non-debuggable dependencies:\n")
    flaggedActions.joinTo(warning, "\n") { (label, action) -> "$label: ${action.arguments.joinToString(" ")}" }

    if (DebugInfoDismissalState.isDismissed(project, target)) return

    val exitCode = showDebugInfoWarning(project, target, configurations.mainConfiguration)

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
fun checkCompileAction(arguments: List<String>, compilerKind: OCCompilerKind?): Boolean {
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

/**
 * Checks that the given link action arguments produce path-independent
 * debug information. Atm only checks for oso_prefix on macOS.
 */
fun checkLinkAction(arguments: List<String>, compilerKind: OCCompilerKind?): Boolean {
  if (compilerKind != ClangCompilerKind || OS.CURRENT != OS.macOS) return true
  return arguments.any { it.contains("-oso_prefix,.") }
}
