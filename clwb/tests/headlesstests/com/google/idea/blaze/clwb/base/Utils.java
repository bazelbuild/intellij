/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
