package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerInfoCache.Session;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath.Kind;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;

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

  /** #api212: inline as IncludedHeadersRoot.create */
  public static IncludedHeadersRoot createIncludedHeadersRoot(
      Project project,
      VirtualFile includedDir,
      boolean recursive,
      boolean preferQuotes,
      Kind kind) {
    return IncludedHeadersRoot.create(project, includedDir, recursive, preferQuotes, kind);
  }
}
