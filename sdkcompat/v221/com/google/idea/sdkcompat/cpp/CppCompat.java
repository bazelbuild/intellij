package com.google.idea.sdkcompat.cpp;

import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Session;

/** Provides SDK compatibility shims for C++ classes, available to CLion. */
public final class CppCompat {
  private CppCompat() {}

  /** #api212: inline into BlazeCWorkspace.collectCompilerSettingsInParallel */
  public static void scheduleInSession(
      Session<Integer> session,
      int id,
      OCResolveConfiguration.ModifiableModel config,
      CidrToolEnvironment toolEnvironment,
      String workspacePath) {
    session.schedule(id, config, toolEnvironment, workspacePath);
  }
}
