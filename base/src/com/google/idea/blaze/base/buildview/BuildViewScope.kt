@file:Suppress("UnstableApiUsage")

package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.BlazeScope
import com.google.idea.blaze.base.scope.OutputSink
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.output.StatusOutput
import com.google.idea.blaze.common.Output
import com.google.idea.blaze.common.PrintOutput
import com.intellij.build.BuildDescriptor
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.SyncViewManager
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.util.function.Consumer

class BuildViewScope(project: Project, private val title: String) : BlazeScope {
  companion object {
    @JvmStatic
    fun of(ctx: BlazeContext): BuildViewScope? = ctx.getScope(BuildViewScope::class.java)
  }

  private var progress = SyncViewManager.createBuildProgress(project)

  override fun onScopeBegin(ctx: BlazeContext) {
    progress.start(ProgressDescriptor(title, ctx))

    addOutputSink<PrintOutput>(ctx) {
      progress.output(it.text + '\n', true)
    }
    addOutputSink<StatusOutput>(ctx) {
      progress.output(it.status + '\n', true)
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
  }

  fun startProgress(title: String): BuildProgress<BuildProgressDescriptor>? {
    return progress?.progress(title)
  }
}

private class ProgressDescriptor(private val title: String, ctx: BlazeContext) :
  BuildProgressDescriptor {
  private val descriptor = DefaultBuildDescriptor(Any(), title, "", System.currentTimeMillis())
    .withRestartAction(RestartAction(ctx))

  override fun getTitle(): String = title

  override fun getBuildDescriptor(): BuildDescriptor = descriptor
}

private class RestartAction(private val ctx: BlazeContext) :
  AnAction({ "Stop" }, AllIcons.Actions.Suspend) {
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
