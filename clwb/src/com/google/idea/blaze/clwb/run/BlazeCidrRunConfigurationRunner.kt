/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.buildview.RunConfigBuild
import com.google.idea.blaze.base.buildview.execute
import com.google.idea.blaze.base.buildview.launchBuild
import com.google.idea.blaze.base.buildview.println
import com.google.idea.blaze.base.buildview.tryExecute
import com.google.idea.blaze.base.command.BlazeInvocationContext
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.ExecutorType
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner
import com.google.idea.blaze.base.util.SaveUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.jetbrains.cidr.execution.CidrCommandLineState
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** CLion-specific handler for [BlazeCommandRunConfiguration]s. */
class BlazeCidrRunConfigurationRunner(private val configuration: BlazeCommandRunConfiguration) :
  BlazeCommandRunConfigurationRunner {

  /** Calculated during the before-run task. */
  @JvmField
  var executable: Path? = null

  override fun getRunProfileState(executor: Executor, env: ExecutionEnvironment): RunProfileState {
    return CidrCommandLineState(env, BlazeCidrLauncher(configuration, this, env))
  }

  override fun executeBeforeRunTask(env: ExecutionEnvironment): Boolean {
    executable = null

    try {
      executable = getExecutableToDebug(env)
      return true
    } catch (e: ExecutionException) {
      ExecutionUtil.handleExecutionError(env.project, env.executor.toolWindowId, env.runProfile, e)
      return false
    }
  }

  /**
   * Builds blaze C/C++ target in debug mode, and returns the output build artifact.
   * 
   * @throws ExecutionException if no unique output artifact was found.
   */
  @Throws(ExecutionException::class)
  private fun getExecutableToDebug(env: ExecutionEnvironment): Path {
    SaveUtil.saveAllFiles()

    val target: Label = getSingleTarget(configuration)

    val executableFuture = launchBuild(env.project, "Build $target") { ctx ->
      val invocationContext = BlazeInvocationContext.runConfigContext(
        /* executorType = */ ExecutorType.fromExecutor(env.executor),
        /* configurationType = */ configuration.type,
        /* beforeRunTask = */ true,
      )

      val config = DiscoverTargetConfigurations(env.project, configuration, invocationContext, target).tryExecute(ctx)
      val build = RunConfigBuild(env.project, configuration, invocationContext, target).execute(ctx)

      if (config != null) {
        ctx.println("Target configuration: ${config.mainTarget} (${config.mainConfiguration})")

        if (isDebugging(env)) {
          DebugInfoCheck(env.project, config).execute(ctx)
        }
      }

      build.executable
    }

    try {
      return executableFuture.get()
    } catch (_: InterruptedException) {
      throw RunCanceledByUserException()
    } catch (_: CancellationException) {
      throw RunCanceledByUserException()
    } catch (e: java.util.concurrent.ExecutionException) {
      throw ExecutionException("Failed to get output artifacts when building $target", e)
    }
  }
}

private fun isDebugging(environment: ExecutionEnvironment): Boolean {
  return environment.executor is DefaultDebugExecutor
}

@Throws(ExecutionException::class)
private fun getSingleTarget(config: BlazeCommandRunConfiguration): Label {
  val targets = config.targets
  if (targets.size != 1 || targets[0] !is Label) {
    throw ExecutionException("Invalid configuration: doesn't have a single target label")
  }

  return targets[0] as Label
}
