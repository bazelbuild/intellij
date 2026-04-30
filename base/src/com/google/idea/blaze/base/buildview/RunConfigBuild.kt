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
package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import java.io.File
import java.nio.file.Path

private const val DEFAULT_OUTPUT_GROUP_NAME = "default"

class RunConfigBuild @JvmOverloads constructor(
  private val project: Project,
  private val configuration: BlazeCommandRunConfiguration,
  private val invocationContext: BlazeInvocationContext,
  private val target: Label,
  private val additionalFlags: List<String> = emptyList(),
) : BuildStep<RunConfigBuild.Output> {

  override val title: String = "Building $target"

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

    val cmd = BlazeCommand.builder(BlazeCommandName.BUILD)
      .addTargets(target)
      .addBlazeFlags(additionalFlags)
      .addBlazeFlags(flags)
      .addBlazeFlags(externalFlags)

    val result = BazelExecService.of(project).build(ctx, cmd)

    val buildResult = result.buildResult()
    if (buildResult.status != BuildResult.Status.SUCCESS) {
      fail("Build $target failed\n> exit code: ${buildResult.exitCode}\n> out of memory: ${buildResult.outOfMemory()}")
    }

    return Output(target = target, executable = findExecutable(result, target))
  }

  private fun findExecutable(output: BlazeBuildOutputs, target: Label): Path {
    // should only be called if the build succeeds
    require(output.buildResult().status == BuildResult.Status.SUCCESS)

    // manually find local artifacts in favour of LocalFileArtifact.getLocalFiles, to avoid the artifact cache,
    // since atm it does not preserve the executable flag for files (and there might be other issues)
    val artifacts = output.getOutputGroupTargetArtifacts(DEFAULT_OUTPUT_GROUP_NAME, target.toString())
      .filterIsInstance<LocalFileArtifact>()
      .map(LocalFileArtifact::getFile)
      .filter(File::canExecute)
      .toList()

    if (artifacts.isEmpty()) {
      fail("No output artifacts found for $target")
    }

    if (artifacts.size == 1) {
      return artifacts.first().toPath()
    }

    val name = PathUtil.getFileName(target.targetName().toString())
    return artifacts.firstOrNull { it.name == name }?.toPath() ?: fail("No executable found for $target")
  }

  data class Output(
    val target: Label,
    val executable: Path,
  )
}
