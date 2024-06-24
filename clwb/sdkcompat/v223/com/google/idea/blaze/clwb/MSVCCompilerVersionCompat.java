package com.google.idea.blaze.clwb;

import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;

import java.io.File;
import javax.annotation.Nullable;

// #api231
public class MSVCCompilerVersionCompat {
  public record ArchAndVersion() {}

  public static @Nullable ArchAndVersion getCompilerVersion(File compiler) {
    return new ArchAndVersion();
  }

  public static void setEnvironmentVersion(CPPEnvironment environment, ArchAndVersion version) { }
}
