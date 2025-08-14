package com.google.idea.blaze.clwb.base;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import org.jetbrains.annotations.Nullable;

public class TestUtils {

  @Nullable
  public static VirtualFile resolveHeader(String fileName, OCCompilerSettings settings) {
    final var roots = settings.getHeadersSearchRoots().getAllRoots();

    for (final var root : roots) {
      final var rootFile = root.getVirtualFile();
      if (rootFile == null) continue;

      final var headerFile = rootFile.findFileByRelativePath(fileName);
      if (headerFile == null) continue;

      return headerFile;
    }

    return null;
  }

  public static void setIncludesCacheEnabled(boolean enabled) {
    Registry.get("bazel.cpp.sync.allow.bazel.bin.header.search.path").setValue(!enabled);
    Registry.get("bazel.cc.includes.cache.enabled").setValue(enabled);
  }
}
