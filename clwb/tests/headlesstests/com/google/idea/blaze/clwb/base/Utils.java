package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.cpp.sync.HeaderCacheService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches.Format;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class Utils {

  public static List<String> lookupCompilerSwitch(String flag, OCCompilerSettings settings) {
    final var switches = settings.getCompilerSwitches();
    assertThat(switches).isNotNull();

    return switches.getList(Format.BASH_SHELL)
        .stream()
        .map(it -> it.replaceAll("^-+", ""))
        .filter(it -> it.startsWith(flag))
        .map(it -> Strings.trimStart(it.substring(flag.length()), "="))
        .toList();
  }

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
    Registry.get(HeaderCacheService.ENABLED_KEY).setValue(enabled);
  }
}
