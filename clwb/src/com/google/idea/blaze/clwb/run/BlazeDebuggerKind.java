/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.clwb.ToolchainUtils;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;

public enum BlazeDebuggerKind {
  BUNDLED_GDB,
  BUNDLED_LLDB,
  GDB_SERVER;

  /**
   * Determines whether to bundled gdb or gdb server.
   * <p>
   * GDB server is only supported on linux for now:
   * - Mac does not have gdbserver
   * - Windows does not support the gdbwrapper script
   */
  private static BlazeDebuggerKind gdbBundledOrServer() {
    if (!SystemInfo.isLinux) {
      return BUNDLED_GDB;
    }
    if (!Registry.is("bazel.clwb.debug.use.gdb.server")) {
      return BUNDLED_GDB;
    }

    return GDB_SERVER;
  }

  /**
   * Determines the debugger kind based on the default toolchain. The default toolchain can be
   * configured by the user. Therefore, this could return different values depending on the
   * installation.
   */
  public static BlazeDebuggerKind byDefaultToolchain() {
    final var kind = ToolchainUtils.getToolchain().getDebuggerKind();

    return switch (kind) {
      case BUNDLED_GDB, CUSTOM_GDB -> gdbBundledOrServer();
      case BUNDLED_LLDB -> BUNDLED_LLDB;
    };
  }

  /**
   * Determines the debugger kind based on the given compiler kind and system information. Falls
   * back to byDefaultToolchain if the system is not linux, mac or windows.
   */
  public static BlazeDebuggerKind byHeuristic(OCCompilerKind compilerKind) {
    if (SystemInfo.isLinux) {
      return gdbBundledOrServer();
    }
    if (SystemInfo.isMac) {
      return BUNDLED_LLDB;
    }
    if (SystemInfo.isWindows) {
       if (compilerKind instanceof MSVCCompilerKind) {
         return BUNDLED_LLDB;
       } else {
         return BUNDLED_GDB;
       }
    }

    // fallback to default toolchain
    return byDefaultToolchain();
  }

}
