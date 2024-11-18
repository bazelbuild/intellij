@file:Suppress("UnstableApiUsage")

package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.BlazeScope
import com.google.idea.blaze.base.scope.OutputSink
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.output.StatusOutput
import com.google.idea.blaze.common.Output
import com.google.idea.blaze.common.PrintOutput
import com.google.idea.blaze.common.PrintOutput.OutputType
import com.intellij.build.BuildDescriptor
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import java.util.function.Consumer

class BuildViewScope(project: Project, private val title: String) : BlazeScope {

  companion object {
    @JvmStatic
    fun of(ctx: BlazeContext): BuildViewScope? = ctx.getScope(BuildViewScope::class.java)
  }

  private var progress = SyncViewManager.createBuildProgress(project)
  private var console = PtyConsoleView(project)
  private var indicator = BackgroundableProcessIndicator(project, title, "Cancel", "Cancel", true)

  override fun onScopeBegin(ctx: BlazeContext) {
    progress.start(ProgressDescriptor(title, console, ctx))

    indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun cancel() {
        super.cancel()
        ctx.setCancelled()
      }
    })
    indicator.start()

    addOutputSink<PrintOutput>(ctx) {
      when (it.outputType) {
        OutputType.NORMAL, OutputType.LOGGED -> console.write(it.text)
        OutputType.ERROR -> console.error(it.text)
        OutputType.PROCESS -> console.append(it.text)
      }
    }
    addOutputSink<StatusOutput>(ctx) {
      console.write(it.status)
    }
    addOutputSink<IssueOutput>(ctx) {
      progress.buildIssue(it, it.kind)
    }
  }

  private inline fun <reified T : Output> addOutputSink(ctx: BlazeContext, consumer: Consumer<T>) {
    ctx.addOutputSink(T::class.java) { it: T ->
      consumer.accept(it)
      OutputSink.Propagation.Stop
    }
  }

  override fun onScopeEnd(ctx: BlazeContext) {
    val progress = this.progress ?: return

    when {
      ctx.isCancelled -> progress.cancel()
      ctx.hasErrors() -> progress.fail()
      else -> progress.finish()
    }

    indicator.stop()
    indicator.processFinish()
  }

  fun startProgress(title: String): BuildProgress<BuildProgressDescriptor>? {
    indicator.text2 = title
    return progress?.progress(title)
  }
}

private class ProgressDescriptor(
  private val title: String,
  private val console: PtyConsoleView,
  ctx: BlazeContext,
) : BuildProgressDescriptor {

  private val descriptor = DefaultBuildDescriptor(Any(), title, "", System.currentTimeMillis())
    .withRestartAction(RestartAction(ctx))
    .withContentDescriptor(this::getContentDescriptor)
    .apply { isNavigateToError = ThreeState.NO }

  override fun getTitle(): String = title

  override fun getBuildDescriptor(): BuildDescriptor = descriptor

  private fun getContentDescriptor(): RunContentDescriptor {
    return RunContentDescriptor(console, null, console.component, title)
  }
}

private class RestartAction(private val ctx: BlazeContext) : AnAction({ "Stop" }, AllIcons.Actions.Suspend) {

  override fun actionPerformed(event: AnActionEvent) {
    ctx.setCancelled()
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = !ctx.isCancelled && !ctx.isEnding
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
