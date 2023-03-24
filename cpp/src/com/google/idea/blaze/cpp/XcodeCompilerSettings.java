package com.google.idea.blaze.cpp;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents the Xcode settings a C compiler needs to run.
 */
public class XcodeCompilerSettings {
  private final Path developerDir;
  private final Path sdkRoot;

  public XcodeCompilerSettings(Path developerDir, Path sdkRoot) {
    this.developerDir = developerDir;
    this.sdkRoot = sdkRoot;
  }

  public Path getDeveloperDir() {
    return developerDir;
  }

  public Path getSdkRoot() {
    return sdkRoot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    XcodeCompilerSettings that = (XcodeCompilerSettings) o;
    return Objects.equals(getDeveloperDir(), that.getDeveloperDir())
        && Objects.equals(getSdkRoot(), that.getSdkRoot());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDeveloperDir(), getSdkRoot());
  }
}
