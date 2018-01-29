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
package com.google.idea.sdkcompat.clion;

import com.jetbrains.cidr.cpp.CPPToolchains;

/** Handles changes to toolchains between different api versions */
public class ToolchainUtils {
  public static void setDefaultDebuggerPath(String debuggerPath) {
    CPPToolchains.Settings settings = CPPToolchains.getInstance().getState();
    settings.setUseBundledGDB(false);
    settings.setSpecifiedGDBExecutablePath(debuggerPath);
  }

  /**
   * Used to register an instance of this class for unit tests. This needs to be in sdkcompat
   * because CPPToolchains changed packages between 2017.2 and 2017.3. #api173
   */
  public static Class<CPPToolchains> getCppToolchainsClass() {
    return CPPToolchains.class;
  }

  /**
   * Used to register an instance of this class for unit tests. This needs to be in sdkcompat
   * because CPPToolchains changed packages between 2017.2 and 2017.3. #api173
   */
  public static CPPToolchains createCppToolchainsInstance() {
    return new CPPToolchains();
  }
}
