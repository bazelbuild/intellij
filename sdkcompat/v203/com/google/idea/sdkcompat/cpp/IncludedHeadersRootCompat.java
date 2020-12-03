package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;

/**
 * Compat for {@link IncludedHeadersRoot}.
 *
 * <p>#api201
 */
public class IncludedHeadersRootCompat {
  public static boolean isUserHeaders(IncludedHeadersRoot root) {
    return root.getKind() == HeadersSearchPath.Kind.USER;
  }

  public static HeadersSearchRoot create(
      Project project, VirtualFile vf, boolean recursive, boolean isUserHeader) {
    HeadersSearchPath.Kind kind =
        isUserHeader ? HeadersSearchPath.Kind.USER : HeadersSearchPath.Kind.SYSTEM;
    return IncludedHeadersRoot.create(project, vf, recursive, kind);
  }

  private IncludedHeadersRootCompat() {}
}
