package com.google.idea.sdkcompat.cidr;

import com.google.idea.sdkcompat.clion.ToolchainUtils;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;

/** Adapter to bridge different SDK versions. */
public class CPPEnvironmentAdapter extends CPPEnvironment {
  public CPPEnvironmentAdapter() {
    super(ToolchainUtils.getToolchain());
  }
}
