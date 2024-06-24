package com.google.idea.blaze.clwb;

import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.cpp.toolchains.MSVC;
import com.jetbrains.cidr.cpp.toolchains.msvc.MSVCArchAndVersion;
import com.jetbrains.cidr.cpp.toolchains.msvc.MSVCCompilerToVersionCacheService;
import java.io.File;
import javax.annotation.Nullable;

// #api231
public class MSVCCompilerVersionCompat {
  public record ArchAndVersion(MSVCArchAndVersion delegate) {}

  public static @Nullable ArchAndVersion getCompilerVersion(File compiler) {
    final var service = ApplicationManager.getApplication()
        .getService(MSVCCompilerToVersionCacheService.class);

    final var result = service.getCompilerVersion(compiler.getAbsolutePath());
    if (result == null) {
      return null;
    }

    return new ArchAndVersion(result);
  }

  public static void setEnvironmentVersion(CPPEnvironment environment, ArchAndVersion version) {
    final var msvc = (MSVC) environment.getToolSet();
    msvc.setToolsVersion(version.delegate);
  }
}
