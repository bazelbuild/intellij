package com.google.idea.sdkcompat.clion;

import com.jetbrains.cidr.cpp.CPPToolchains;

/** Handles changes to toolchains between different api versions */
public class ToolchainUtils {
  public static void setDefaultDebuggerPath(String debuggerPath) {
    CPPToolchains.Settings settings = CPPToolchains.getInstance().getState();
    settings.setUseBundledGDB(false);
    settings.setSpecifiedGDBExecutablePath(debuggerPath);
  }
}
