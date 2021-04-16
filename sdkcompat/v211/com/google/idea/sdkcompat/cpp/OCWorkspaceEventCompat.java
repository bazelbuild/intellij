package com.google.idea.sdkcompat.cpp;

import com.jetbrains.cidr.lang.workspace.OCWorkspaceEventImpl;

/**
 * Compat methods for {@link OCWorkspaceEventImpl}.
 *
 * <p>#api201
 */
public class OCWorkspaceEventCompat {
  private OCWorkspaceEventCompat() {}

  public static OCWorkspaceEventImpl newEvent(
      boolean resolveConfigurationsChanged,
      boolean sourceFilesChanged,
      boolean compilerSettingsChanged,
      boolean clientVersionChanged) {
    return new OCWorkspaceEventImpl(
        resolveConfigurationsChanged,
        sourceFilesChanged,
        compilerSettingsChanged,
        clientVersionChanged);
  }
}
