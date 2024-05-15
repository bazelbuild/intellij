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
package com.google.idea.blaze.clwb.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.clwb.ToolchainUtils;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PathUtil;
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.execution.debugger.CidrDebuggerPathManager;
import java.io.File;
import javax.annotation.Nullable;

/** CLion-specific class that provides the slightly customized Toolchain for use with gdbserver */
public class BlazeGDBServerProvider {
  private static final Logger logger = Logger.getInstance(BlazeGDBServerProvider.class);

  /**
   * This is a script distributed with the plugin that makes gdbserver behave more like how the
   * environment expects. It will respond to signals, exit with the same exit code as the inferior,
   * and escape the parameters correctly.
   */
  private static final NotNullLazyValue<String> GDBSERVER_WRAPPER =
      new NotNullLazyValue<String>() {
        @Override
        protected String compute() {
          String jarPath = PathUtil.getJarPathForClass(BlazeCidrLauncher.class);
          File pluginrootDirectory = new File(jarPath).getParentFile().getParentFile();
          return new File(pluginrootDirectory, "gdb/gdbserver").getPath();
        }
      };

  private static final BoolExperiment useRemoteDebugging =
      new BoolExperiment("cc.remote.debugging", true);

  private static final BoolExperiment useRemoteDebuggingWrapper =
      new BoolExperiment("cc.remote.debugging.wrapper", true);

  // These flags are used when debugging cc_binary targets when remote debugging
  // is enabled (cc.remote.debugging)
  private static final ImmutableList<String> EXTRA_FLAGS_FOR_DEBUG_RUN =
      ImmutableList.of(
          "--compilation_mode=dbg", "--strip=never", "--dynamic_mode=off");

  // These flags are used when debugging cc_test targets when remote debugging
  // is enabled (cc.remote.debugging)
  private static final ImmutableList<String> EXTRA_FLAGS_FOR_DEBUG_TEST =
      ImmutableList.of(
          "--compilation_mode=dbg",
          "--strip=never",
          "--dynamic_mode=off",
          "--test_timeout=3600",
          BlazeFlags.NO_CACHE_TEST_RESULTS,
          BlazeFlags.EXCLUSIVE_TEST_EXECUTION,
          BlazeFlags.DISABLE_TEST_SHARDING);

  // Allows the fission flag to be disabled as workaround for
  // https://github.com/bazelbuild/intellij/issues/5604
  static ImmutableList<String> getOptionalFissionArguments() {
    if(Registry.is("bazel.clwb.debug.fission.disabled")) {
      return ImmutableList.of();
    } else {
      return ImmutableList.of("--fission=yes");
    }
  }

  static boolean shouldUseGdbserver() {
    // Only provide support for Linux for now:
    // - Mac does not have gdbserver, so use the old gdb method for debugging
    // - Windows does not support the gdbwrapper script
    if (!SystemInfo.isLinux) {
      return false;
    }
    return useRemoteDebugging.getValue();
  }

  static ImmutableList<String> getFlagsForDebugging(RunConfigurationState state) {
    if (!(state instanceof BlazeCidrRunConfigState)) {
      return ImmutableList.of();
    }
    BlazeCidrRunConfigState handlerState = (BlazeCidrRunConfigState) state;
    BlazeCommandName commandName = handlerState.getCommandState().getCommand();
    ImmutableList.Builder<String> builder = ImmutableList.builder();

    CPPToolchains.Toolchain toolchain = ToolchainUtils.getToolchain();
    String gdbServerPath = BlazeGDBServerProvider.getGDBServerPath(toolchain);
    if (gdbServerPath == null) {
      // couldn't find it, fall back to trying PATH
      gdbServerPath = "gdbserver";
    }

    if (useRemoteDebuggingWrapper.getValue()) {
      String runUnderOption =
          String.format(
              "--run_under='%s' '%s' '%s' --once localhost:%d --target",
              "bash",
              GDBSERVER_WRAPPER.getValue(),
              gdbServerPath,
              handlerState.getDebugPortState().port);
      builder.add(runUnderOption);
    } else {
      String runUnderOption =
          String.format(
              "--run_under='%s' --once localhost:%d",
              gdbServerPath, handlerState.getDebugPortState().port);
      builder.add(runUnderOption);
    }
    if (BlazeCommandName.RUN.equals(commandName)) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_RUN);
      builder.addAll(getOptionalFissionArguments());
      return builder.build();
    }
    if (BlazeCommandName.TEST.equals(commandName)) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_TEST);
      builder.addAll(getOptionalFissionArguments());
      return builder.build();
    }
    return ImmutableList.of();
  }

  @Nullable
  private static String getGDBServerPath(CPPToolchains.Toolchain toolchain) {
    String gdbPath;

    if (!shouldUseGdbserver()) {
      logger.error(
          "Trying to resolve gdbserver executable for " + SystemInfo.getOsNameAndVersion());
      return null;
    }

    CPPDebugger.Kind debuggerKind = toolchain.getDebuggerKind();
    switch (debuggerKind) {
      case CUSTOM_GDB:
        gdbPath = toolchain.getCustomGDBExecutablePath();
        break;
      case BUNDLED_GDB:
        gdbPath = CidrDebuggerPathManager.getBundledGDBBinary().getPath();
        break;
      default:
        logger.error("Trying to resolve gdbserver executable for " + debuggerKind.toString());
        return null;
    }

    // We are going to just try to append "server" to the gdb executable path - it would be nicer
    // to have this stored as part of the toolchain configuration, but it isn't.
    File gdbServer = new File(gdbPath + "server");
    if (!gdbServer.exists()) {
      return null;
    }
    return gdbServer.getAbsolutePath();
  }

}
