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
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.common.aquery.ActionGraph
import com.intellij.execution.RunManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.SwingConstants

private const val NOTIFICATION_TITLE = "Debug Info Warning"
private const val DOC_URL = "https://github.com/bazelbuild/intellij/blob/master/docs/cpp/debugging.md"

internal const val INJECT_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE
internal const val DISMISS_TARGET_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 1
internal const val DISMISS_PROJECT_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 2

/**
 * Shows the debug info warning dialog and returns the exit code chosen by the user.
 */
internal fun showDebugInfoWarning(
  project: Project,
  target: Label,
  configuration: ActionGraph.Configuration,
): Int {
  var exitCode = DialogWrapper.CANCEL_EXIT_CODE
  ApplicationManager.getApplication().invokeAndWait {
    val dialog = DebugInfoWarningDialog(project, target, configuration)
    dialog.show()
    exitCode = dialog.exitCode
  }

  return exitCode
}

/**
 * Re-queues [env]'s run configuration via the normal execution infrastructure. Intended to be
 * called right before aborting the current [DebugInfoCheck] with fail so the next attempt picks
 * up the updated project view.
 */
internal fun rerunRunConfiguration(env: ExecutionEnvironment) {
  val config = env.runProfile as? BlazeCommandRunConfiguration ?: return
  val settings = RunManager.getInstance(env.project).findSettings(config) ?: return

  ApplicationManager.getApplication().invokeAndWait {
    ExecutionUtil.runConfiguration(settings, env.executor)
  }
}

private class DebugInfoWarningDialog(
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
    row {
      text("Learn more <a>here</a> about this check and how to resolve it.") {
        BrowserUtil.browse(DOC_URL)
      }
    }
  }

  override fun createActions(): Array<Action> = arrayOf(
    okAction,
    cancelAction,
    DialogWrapperExitAction("Dismiss for Target", DISMISS_TARGET_EXIT_CODE),
    DialogWrapperExitAction("Dismiss for Project", DISMISS_PROJECT_EXIT_CODE),
  )
}
