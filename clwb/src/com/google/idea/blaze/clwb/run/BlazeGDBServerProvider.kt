/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.run.state.RunConfigurationState
import com.google.idea.blaze.clwb.ToolchainUtils
import com.google.idea.common.experiments.BoolExperiment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PathUtil
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.execution.debugger.CidrDebuggerPathManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private val LOG = logger<BlazeGDBServerProvider>()

private val USE_REMOTE_DEBUGGING_WRAPPER: BoolExperiment = BoolExperiment("cc.remote.debugging.wrapper", true)

private const val GDB_SERVER_PROPERTY = "clwb.gdbserverPath"

/** CLion-specific class that provides the slightly customized Toolchain for use with gdbserver  */
object BlazeGDBServerProvider {

  /**
   * This is a script distributed with the plugin that makes gdbserver behave more like how the
   * environment expects. It will respond to signals, exit with the same exit code as the inferior,
   * and escape the parameters correctly.
   */
  private val GDBSERVER_WRAPPER: String by lazy {
    if (System.getProperty(GDB_SERVER_PROPERTY) != null) {
      Path.of(System.getProperty(GDB_SERVER_PROPERTY)).absolutePathString()
    } else {
      val jarPath = Path.of(PathUtil.getJarPathForClass(BlazeCidrLauncher::class.java))
      jarPath.parent.parent.resolve("gdb").resolve("gdbserver").toString()
    }
  }

  // These flags are used when debugging cc_binary targets when remote debugging
  // is enabled (cc.remote.debugging)
  private val EXTRA_FLAGS_FOR_DEBUG_RUN = ImmutableList.of<String>(
    "--compilation_mode=dbg", "--strip=never", "--dynamic_mode=off"
  )

  // These flags are used when debugging cc_test targets when remote debugging
  // is enabled (cc.remote.debugging)
  private val EXTRA_FLAGS_FOR_DEBUG_TEST = ImmutableList.of<String>(
    "--compilation_mode=dbg",
    "--strip=never",
    "--dynamic_mode=off",
    "--test_timeout=3600",
    BlazeFlags.NO_CACHE_TEST_RESULTS,
    BlazeFlags.EXCLUSIVE_TEST_EXECUTION,
    BlazeFlags.DISABLE_TEST_SHARDING
  )

  // Allows the fission flag to be disabled as workaround for
  @JvmStatic
  fun getOptionalFissionArguments(): ImmutableList<String> {
    return if (Registry.`is`("bazel.clwb.debug.fission.disabled")) {
      ImmutableList.of()
    } else {
      ImmutableList.of("--fission=yes")
    }
  }

  @JvmStatic
  fun getFlagsForDebugging(state: RunConfigurationState?): ImmutableList<String> {
    if (state !is BlazeCidrRunConfigState) {
      return ImmutableList.of()
    }

    val commandName = state.commandState.command
    val builder = ImmutableList.builder<String>()

    val toolchain = ToolchainUtils.getToolchain()

    // if gdbserver could not be found, fall back to trying PATH
    val gdbServerPath = getGDBServerPath(toolchain) ?: "gdbserver"

    if (USE_REMOTE_DEBUGGING_WRAPPER.value) {
      builder.add(
        String.format(
          "--run_under='bash' '%s' '%s' --once localhost:%d --target",
          GDBSERVER_WRAPPER,
          gdbServerPath,
          state.getDebugPortState().port,
        )
      )
    } else {
      builder.add(
        String.format(
          "--run_under='%s' --once localhost:%d",
          gdbServerPath,
          state.getDebugPortState().port,
        )
      )
    }

    if (BlazeCommandName.RUN == commandName) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_RUN)
      builder.addAll(getOptionalFissionArguments())
      return builder.build()
    }

    if (BlazeCommandName.TEST == commandName) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_TEST)
      builder.addAll(getOptionalFissionArguments())
      return builder.build()
    }

    return ImmutableList.of()
  }

  private fun getGDBServerPath(toolchain: CPPToolchains.Toolchain): String? {
    // TODO: this still depends on the default toolchain
    val gdbPath = when (toolchain.debuggerKind) {
      CPPDebugger.Kind.CUSTOM_GDB -> toolchain.customGDBExecutablePath
      CPPDebugger.Kind.BUNDLED_GDB -> CidrDebuggerPathManager.getBundledGDBBinary().path

      else -> {
        LOG.error("Trying to resolve gdbserver executable for ${toolchain.debuggerKind}")
        return null
      }
    }

    // We are going to just try to append "server" to the gdb executable path - it would be nicer
    // to have this stored as part of the toolchain configuration, but it isn't.
    val gdbServer = Path.of(gdbPath + "server")
    if (!Files.exists(gdbServer)) {
      return null
    }

    return gdbServer.absolutePathString()
  }
}
