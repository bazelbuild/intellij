package com.google.idea.sdkcompat.cpp;

import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain;

/** Api compat with 2020.1 #api193 */
public class ToolchainCompat {
  public static String getDefaultName() {
    return Toolchain.DEFAULT;
  }
}
