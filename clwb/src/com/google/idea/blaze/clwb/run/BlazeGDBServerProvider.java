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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.clwb.ToolchainUtils;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.sdkcompat.clion.CPPToolSetWithHomeAndSeparators;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PathUtil;
import com.jetbrains.cidr.cpp.toolchains.CPPDebugger;
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain;
import com.jetbrains.cidr.execution.debugger.CidrDebuggerPathManager;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment.PrepareFor;
import com.jetbrains.cidr.toolchains.OSType;
import java.io.File;
import java.util.List;
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
          "--compilation_mode=dbg", "--strip=never", "--dynamic_mode=off", "--fission=yes");

  // These flags are used when debugging cc_test targets when remote debugging
  // is enabled (cc.remote.debugging)
  private static final ImmutableList<String> EXTRA_FLAGS_FOR_DEBUG_TEST =
      ImmutableList.of(
          "--compilation_mode=dbg",
          "--strip=never",
          "--dynamic_mode=off",
          "--fission=yes",
          "--test_timeout=3600",
          BlazeFlags.NO_CACHE_TEST_RESULTS,
          BlazeFlags.EXCLUSIVE_TEST_EXECUTION,
          BlazeFlags.DISABLE_TEST_SHARDING);

  static CPPToolchains.Toolchain getToolchain(Project project) {
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    File workspaceRootDirectory = workspaceRoot.directory();
    CPPToolchains.Toolchain toolchainForDebugger =
        new Toolchain(OSType.getCurrent()) {
          private final CPPToolSet blazeToolSet = new BlazeToolSet(workspaceRootDirectory);

          @Override
          public CPPToolSet getToolSet() {
            return blazeToolSet;
          }
        };

    ToolchainUtils.setDebuggerToDefault(toolchainForDebugger);

    return toolchainForDebugger;
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

  static ImmutableList<String> getFlagsForDebugging(
      CPPToolchains.Toolchain toolchain, RunConfigurationState state) {
    if (!(state instanceof BlazeCidrRunConfigState)) {
      return ImmutableList.of();
    }
    BlazeCidrRunConfigState handlerState = (BlazeCidrRunConfigState) state;
    BlazeCommandName commandName = handlerState.getCommandState().getCommand();
    ImmutableList.Builder<String> builder = ImmutableList.builder();

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
      return builder.build();
    }
    if (BlazeCommandName.TEST.equals(commandName)) {
      builder.addAll(EXTRA_FLAGS_FOR_DEBUG_TEST);
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

  /**
   * There is currently no way to override the working directory for the debug process when we
   * create it. By creating a CPPToolSet, we have an opportunity to alter the commandline before it
   * launches. See https://youtrack.jetbrains.com/issue/CPP-8362
   */
  private static class BlazeToolSet extends CPPToolSetWithHomeAndSeparators {
    private static final ImmutableSet<CPPDebugger.Kind> SUPPORTED_DEBUGGER_KINDS =
        ImmutableSet.of(CPPDebugger.Kind.BUNDLED_GDB, CPPDebugger.Kind.CUSTOM_GDB);

    private BlazeToolSet(File workingDirectory) {
      super(Kind.MINGW, workingDirectory);
    }

    @Override
    public String readVersion() {
      return "no version";
    }

    @Override
    public String checkVersion(String s) {
      return null;
    }

    @Override
    public File getGDBPath() {
      Toolchain toolchain = ToolchainUtils.getToolchain();
      if (toolchain.getDebuggerKind() == CPPDebugger.Kind.BUNDLED_GDB) {
        return CidrDebuggerPathManager.getBundledGDBBinary();
      }

      String gdbPath = toolchain.getCustomGDBExecutablePath();
      if (gdbPath == null) {
        return CidrDebuggerPathManager.getBundledGDBBinary();
      }
      File gdbFile = new File(gdbPath);
      if (!gdbFile.exists()) {
        return CidrDebuggerPathManager.getBundledGDBBinary();
      }
      return gdbFile;
    }

    @Override
    public void prepareEnvironment(
        GeneralCommandLine cl, PrepareFor prepareFor, List<Option> options)
        throws ExecutionException {
      super.prepareEnvironment(cl, prepareFor, options);
      if (prepareFor.equals(PrepareFor.RUN)) {
        cl.setWorkDirectory(super.getHome());
      }
    }

    @Override
    public boolean supportsDebugger(CPPDebugger.Kind kind) {
      return SUPPORTED_DEBUGGER_KINDS.contains(kind);
    }
  }
}
