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
package com.google.idea.blaze.clwb.run

import com.google.common.collect.ImmutableList
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.system.OS
import com.jetbrains.cidr.lang.workspace.compiler.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val LOG = logger<BazelDebugFlagsBuilder>()

private val VALID_GDB_COMPILERS = listOf(GCCCompilerKind)
private val VALID_LLDB_COMPILERS = listOf(ClangCompilerKind, ClangClCompilerKind, MSVCCompilerKind)

/**
 * Builds flags for debugging a blaze target, flags are either used for just
 * building a binary [BlazeCidrRunConfigurationRunner] or for running actually
 * running the binary [BlazeGDBServerProvider].
 *
 * In theory we would like to have one builder that builds a BlazeCommand to
 * have full control over the environment in the builder.
 */
class BazelDebugFlagsBuilder(
  private val debuggerKind: BlazeDebuggerKind,
  private val compilerKind: OCCompilerKind,
  private val targetOS: OS,
  private val withClangTrimPaths: Boolean = true,
  private val withFissionFlag: Boolean = false,
) {

  init {

    // check for valid debugger/compiler combinations
    when (debuggerKind) {
      BlazeDebuggerKind.BUNDLED_GDB, BlazeDebuggerKind.GDB_SERVER -> LOG.assertTrue(compilerKind in VALID_GDB_COMPILERS)
      BlazeDebuggerKind.BUNDLED_LLDB -> LOG.assertTrue(compilerKind in VALID_LLDB_COMPILERS)
    }
  }

  companion object {

    @JvmStatic
    fun fromDefaults(
      debuggerKind: BlazeDebuggerKind,
      compilerKind: OCCompilerKind,
    ) = BazelDebugFlagsBuilder(
      debuggerKind,
      compilerKind,
      OS.CURRENT,
      withClangTrimPaths = Registry.`is`("bazel.trim.absolute.path.disabled"),
      withFissionFlag = !Registry.`is`("bazel.clwb.debug.fission.disabled"),
    )
  }

  private val flags = ImmutableList.builder<String>()

  fun withBuildFlags(workspaceRoot: String? = null) {
    flags.add("--compilation_mode=dbg")
    flags.add("--strip=never")
    flags.add("--dynamic_mode=off")

    val switchBuilder = when (compilerKind) {
      MSVCCompilerKind -> MSVCSwitchBuilder()
      ClangClCompilerKind -> ClangClSwitchBuilder()
      ClangCompilerKind -> ClangSwitchBuilder()
      else -> GCCSwitchBuilder() // default to GCC, as usual
    }

    switchBuilder.withDebugInfo(2) // ignored for msvc/clangcl
    switchBuilder.withDisableOptimization()

    if (debuggerKind == BlazeDebuggerKind.BUNDLED_LLDB && withClangTrimPaths && workspaceRoot != null) {
      switchBuilder.withSwitch("-fdebug-compilation-dir=\"$workspaceRoot\"")
    }

    if (debuggerKind == BlazeDebuggerKind.GDB_SERVER && withFissionFlag) {
      switchBuilder.withSwitch("--fission=yes")
    }

    flags.addAll(switchBuilder.buildRaw().map { "--copt=$it" })

    if (debuggerKind == BlazeDebuggerKind.BUNDLED_LLDB && targetOS == OS.macOS) {
      flags.add("--linkopt=-Wl,-oso_prefix,.", "--linkopt=-Wl,-reproducible", "--remote_download_regex='.*_objs/.*.o$'")
    }
  }

  fun withTestFlags(timeout: Duration? = 60.minutes) {
    flags.add("--nocache_test_results")
    flags.add("--test_strategy=exclusive")
    flags.add("--test_sharding_strategy=disabled")

    if (timeout != null) {
      flags.add("--test_timeout=${timeout.inWholeSeconds}")
    }
  }

  fun withRunUnderGDBServer(gdbserver: String, port: Int, wrapperScript: String?) {
    if (wrapperScript != null) {
      flags.add("--run_under='bash' '$wrapperScript' '$gdbserver' --once localhost:$port --target")
    } else {
      flags.add("--run_under='$gdbserver' --once localhost:$port")
    }
  }

  fun build(): ImmutableList<String> = flags.build()
}