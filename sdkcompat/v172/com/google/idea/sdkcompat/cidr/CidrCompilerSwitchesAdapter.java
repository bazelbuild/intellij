package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import java.util.List;

/** Adapter to bridge different SDK versions. */
public class CidrCompilerSwitchesAdapter {
  /** Old interface does not know anything about CidrCompilerSwitches.Format */
  public static List<String> getFileArgs(CidrCompilerSwitches switches) {
    return switches.getList(CidrCompilerSwitches.Format.RAW);
  }

  public static List<String> getCommandLineArgs(CidrCompilerSwitches switches) {
    return switches.getList(CidrCompilerSwitches.Format.BASH_SHELL);
  }

  public static String getCommandLineString(CidrCompilerSwitches switches) {
    return StringUtil.join(getCommandLineArgs(switches), " ");
  }
}
