/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb;

import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain;
import com.jetbrains.cidr.toolchains.OSType;

/** Handles changes to toolchains between different api versions */
public class ToolchainUtils {
  public static Toolchain getToolchain() {
    Toolchain toolchain = CPPToolchains.getInstance().getDefaultToolchain();
    if (toolchain == null) {
      toolchain = new Toolchain(OSType.getCurrent());
      toolchain.setName(Toolchain.DEFAULT);
    }
    return toolchain;
  }

  public static void setDebuggerToDefault(Toolchain toolchain) {
    Toolchain defaultToolchain = getToolchain();
    toolchain.setDebugger(defaultToolchain.getDebugger());
  }
}
