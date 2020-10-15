package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import com.jetbrains.cidr.lang.workspace.headerRoots.IncludedHeadersRoot;

/**
 * Compat for {@link IncludedHeadersRoot}.
 *
 * <p>#api201
 */
public class IncludedHeadersRootCompat {
  public static boolean isUserHeaders(IncludedHeadersRoot root) {
    return root.isUserHeaders();
  }

  public static HeadersSearchRoot create(
      Project project, VirtualFile vf, boolean recursive, boolean isUserHeader) {
    return IncludedHeadersRoot.create(project, vf, recursive, isUserHeader);
  }

  private IncludedHeadersRootCompat() {}
}
