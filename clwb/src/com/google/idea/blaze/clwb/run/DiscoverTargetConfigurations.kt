package com.google.idea.blaze.clwb.run

import com.google.idea.blaze.base.buildview.BazelExecService
import com.google.idea.blaze.base.buildview.BuildStep
import com.google.idea.blaze.base.buildview.fail
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.common.aquery.ActionGraph
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import java.io.IOException

private const val CC_COMPILE_MNEMONIC = "CppCompile"

class DiscoverTargetConfigurations(
  private val project: Project,
  private val configuration: BlazeCommandRunConfiguration,
  private val invocationContext: BlazeInvocationContext,
  private val target: Label,

  @Deprecated("Should only be used for backwards compatibility, do not inject extra build flags.")
  private val additionalFlags: List<String> = emptyList(),
) : BuildStep<DiscoverTargetConfigurations.Output> {

  override val title: String = "Discover CC Target Configurations"

  override fun run(ctx: BlazeContext): Output {
    val projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet()
    val handlerState = configuration.handler.state.asSafely<BlazeCommandRunConfigurationCommonState>()

    val flags = BlazeFlags.blazeFlags(
      project,
      projectViewSet,
      BlazeCommandName.BUILD,
      ctx,
      invocationContext,
    )

    val externalFlags = handlerState
      ?.blazeFlagsState
      ?.flagsForExternalProcesses
      ?: emptyList()

    val cmd = BlazeCommand.builder(BlazeCommandName.AQUERY)
      .addBlazeFlags("deps($target)")
      .addBlazeFlags(additionalFlags)
      .addBlazeFlags(flags)
      .addBlazeFlags(externalFlags)
      .addBlazeFlags("--output=streamed_proto")

    BazelExecService.of(project).exec(ctx, cmd).use { result ->
      result.throwOnFailure()

      val graph = try {
        ActionGraph.fromProto(result.stdout)
      } catch (e: IOException) {
        fail("failed to parse action graph", e)
      }

      val configurations = graph.targets.mapNotNull { target ->
        target.compileAction()?.let { Label.create(target.label) to it }
      }.toMap()

      return Output(
        mainTarget = Label.create(graph.defaultTarget.label),
        mainConfiguration = graph.defaultConfiguration,
        compileActions = configurations,
      )
    }
  }

  data class Output(
    val mainTarget: Label,
    val mainConfiguration: ActionGraph.Configuration,
    val compileActions: Map<Label, ActionGraph.Action>,
  )
}

private fun ActionGraph.Target.compileAction(): ActionGraph.Action? {
  return actions.firstOrNull { it.mnemonic == CC_COMPILE_MNEMONIC }
}