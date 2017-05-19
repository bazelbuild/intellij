package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;

/** Adapter to bridge different SDK versions. */
public interface OCResolveConfigurationAdapter extends OCResolveConfiguration {
  /* v162/v163 */
  public VirtualFile getPrecompiledHeader();

  /* v162/v163 */
  public OCLanguageKind getPrecompiledLanguageKind();
}
