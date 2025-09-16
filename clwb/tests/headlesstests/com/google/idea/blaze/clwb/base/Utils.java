package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.util.text.Strings;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches.Format;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import java.util.List;

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
}
