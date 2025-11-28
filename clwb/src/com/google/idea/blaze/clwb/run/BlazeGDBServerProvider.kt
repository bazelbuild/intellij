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
import com.google.idea.blaze.base.run.state.RunConfigurationState
import com.google.idea.blaze.clwb.ToolchainUtils
import com.google.idea.common.util.Datafiles
import com.google.idea.sdkcompat.clion.debug.CidrDebuggerPathManagerAdapter
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PathUtil
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private val LOG = logger<BlazeGDBServerProvider>()

/** CLion-specific class that provides the slightly customized Toolchain for use with gdbserver  */
object BlazeGDBServerProvider {

  /**
   * This is a script distributed with the plugin that makes gdbserver behave more like how the
   * environment expects. It will respond to signals, exit with the same exit code as the inferior,
   * and escape the parameters correctly.
   */
  private val GDBSERVER_WRAPPER: Path by Datafiles.resolveLazy("gdb/gdbserver")

  @JvmStatic
  fun getFlagsForDebugging(state: RunConfigurationState?): ImmutableList<String> {
    if (state !is BlazeCidrRunConfigState) {
      return ImmutableList.of()
    }

    val builder = BazelDebugFlagsBuilder.fromDefaults(BlazeDebuggerKind.GDB_SERVER, GCCCompilerKind)
    builder.withBuildFlags()

    if (state.commandState.command == BlazeCommandName.TEST) {
      builder.withTestFlags()
    }

    // if gdbserver could not be found, fall back to trying PATH
    val gdbServerPath = getGDBServerPath(ToolchainUtils.getToolchain()) ?: "gdbserver"
    builder.withRunUnderGDBServer(gdbServerPath, state.getDebugPortState().port, GDBSERVER_WRAPPER)

    return builder.build()
  }

  private fun getGDBServerPath(toolchain: CPPToolchains.Toolchain): String? {
    // TODO: this still depends on the default toolchain
    val gdbPath = when (toolchain.debuggerKind) {
      CPPDebugger.Kind.CUSTOM_GDB -> toolchain.customGDBExecutablePath
      CPPDebugger.Kind.BUNDLED_GDB -> CidrDebuggerPathManagerAdapter.getBundledGDBBinary().path

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
