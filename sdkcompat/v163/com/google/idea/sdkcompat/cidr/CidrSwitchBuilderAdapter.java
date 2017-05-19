package com.google.idea.sdkcompat.cidr;

import com.google.common.base.Joiner;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import java.util.List;
import java.util.stream.Collectors;

/** Adapter to bridge different SDK versions. */
public class CidrSwitchBuilderAdapter extends CidrSwitchBuilder {
  /**
   * Old CidrSwitchBuilder is unable to deal with options with spaces embedded. This is a hack to
   * preserve the old behaviour for 2016.3 Original hack explanation: - this list of switches is
   * currently only used in one place -- GCCCompiler.tryRunGCC. - list is written to an argument
   * file, whitespace-separated, then passed as a @file arg to clang. In this context, escaped
   * whitespace within a single arg is not handled. Currently, the only way (short of using
   * reflection) to ensure unescaped whitespace is to have CidrSwitchBuilder treat whitespace as a
   * delimiter between args.
   */
  public CidrSwitchBuilderAdapter addAllRaw(List<String> switches) {
    switches = switches.stream().map(flag -> flag.replace("\\ ", " ")).collect(Collectors.toList());
    addAll(Joiner.on(" ").join(switches), CidrSwitchBuilder.Format.FILE_ARGS);
    return this;
  }
}
