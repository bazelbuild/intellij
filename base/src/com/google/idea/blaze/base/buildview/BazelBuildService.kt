/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.buildview

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.command.buildresult.GetArtifactsException
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.model.primitives.TargetExpression
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.google.idea.blaze.base.scope.output.StatusOutput
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.intellij.execution.ExecutionException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.asListenableFuture
import java.io.File
import java.nio.file.Path

private const val DEFAULT_OUTPUT_GROUP_NAME = "default"

@Service(Service.Level.PROJECT)
class BazelBuildService(private val project: Project, private val scope: CoroutineScope) {

  companion object {

    @JvmStatic
    fun buildForRunConfig(
      project: Project,
      configuration: BlazeCommandRunConfiguration,
      invocationContext: BlazeInvocationContext,
      requiredFlags: List<String>,
      overridableFlags: List<String>,
      target: Label,
    ): ListenableFuture<Path> = project.service<BazelBuildService>().buildForRunConfig(
      configuration,
      invocationContext,
      requiredFlags,
      overridableFlags,
      target,
    )
  }

  private fun <T> executionScope(block: suspend (BlazeContext) -> T): Deferred<T> {
    return scope.async(Dispatchers.Default) {
      BlazeContext.create().use { ctx ->
        ctx.pushJob {
          block(ctx)
        }
      }
    }
  }

  @Throws(ExecutionException::class, GetArtifactsException::class)
  private fun buildForRunConfig(
    configuration: BlazeCommandRunConfiguration,
    invocationContext: BlazeInvocationContext,
    requiredFlags: List<String>,
    overridableFlags: List<String>,
    target: Label,
  ): ListenableFuture<Path> {
    val handlerState = configuration.handler.state.asSafely<BlazeCommandRunConfigurationCommonState>()

    val handlerRequiredFlags = handlerState
      ?.blazeFlagsState
      ?.flagsForExternalProcesses
      ?.let(requiredFlags::plus)
      ?: requiredFlags

    return executionScope { ctx ->
      val output = executeBuild(
        ctx = ctx,
        project = project,
        customBazelBinary = handlerState?.blazeBinaryState?.blazeBinary?.let(Path::of),
        invocationContext = invocationContext,
        requiredFlags = handlerRequiredFlags,
        overridableFlags = overridableFlags,
        targets = ImmutableList.of(target),
      )

      findExecutable(ctx, output, target)
    }.asListenableFuture()
  }
}

private fun getProgressMessage(targets: ImmutableList<TargetExpression>): String {
  val builder = StringBuilder("Building ")

  if (targets.isNotEmpty()) {
    builder.append(targets[0])
  }

  if (targets.size > 1) {
    builder.append(" and other targets")
  }

  return builder.toString()
}

@Throws(ExecutionException::class)
private fun executeBuild(
  ctx: BlazeContext,
  project: Project,
  customBazelBinary: Path?,
  invocationContext: BlazeInvocationContext,
  requiredFlags: List<String>,
  overridableFlags: List<String>,
  targets: ImmutableList<TargetExpression>,
): BlazeBuildOutputs.Legacy {
  val projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet()
  val progressMessage = getProgressMessage(targets)

  ctx.push(BuildViewScope.forBuild(project, progressMessage))
  ctx.output(StatusOutput(progressMessage))

  val flags = BlazeFlags.blazeFlags(
    project,
    projectViewSet,
    BlazeCommandName.BUILD,
    ctx,
    invocationContext,
  )

  val commandBuilder = if (customBazelBinary != null)
    BlazeCommand.builder(
      customBazelBinary.toString(),
      BlazeCommandName.BUILD,
    )
  else
    BlazeCommand.builder(
      Blaze.getBuildSystemProvider(project).buildSystem.getDefaultInvoker(project),
      BlazeCommandName.BUILD,
    )

  commandBuilder.addTargets(targets)
    .addBlazeFlags(overridableFlags)
    .addBlazeFlags(flags)
    .addBlazeFlags(requiredFlags)

  val result = BazelExecService.instance(project).build(ctx, commandBuilder)

  if (result.buildResult().status != BuildResult.Status.SUCCESS) {
    throw ExecutionException("$progressMessage failed")
  } else {
    return result
  }
}

@Throws(GetArtifactsException::class)
private fun reportArtifactsIssue(ctx: BlazeContext, msg: String): Nothing {
  IssueOutput.error("OUTPUT_ARTIFACTS").withDescription(msg).submit(ctx)
  throw GetArtifactsException(msg)
}

@Throws(GetArtifactsException::class)
private fun findExecutable(ctx: BlazeContext, output: BlazeBuildOutputs.Legacy, target: Label): Path {
  // should only be called if the build succeeds
  require(output.buildResult().status == BuildResult.Status.SUCCESS)

  // manually find local artifacts in favour of LocalFileArtifact.getLocalFiles, to avoid the artifacts cache
  // the artifacts cache atm does not preserve the executable flag for files (and there might be other issues)
  val artifacts = output.getOutputGroupTargetArtifacts(DEFAULT_OUTPUT_GROUP_NAME, target.toString())
    .filterIsInstance<LocalFileArtifact>()
    .map(LocalFileArtifact::getFile)
    .filter(File::canExecute)
    .toList()

  if (artifacts.isEmpty()) {
    reportArtifactsIssue(ctx, "No output artifacts found for build $target")
  }

  val name = PathUtil.getFileName(target.targetName().toString())

  return artifacts.firstOrNull { it.name == name }?.toPath()
    ?: reportArtifactsIssue(ctx, "No executable found for build $target")
}
