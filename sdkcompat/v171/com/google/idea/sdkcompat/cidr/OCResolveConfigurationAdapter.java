package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;

/** Adapter to bridge different SDK versions. */
public abstract class OCResolveConfigurationAdapter extends UserDataHolderBase
    implements OCResolveConfiguration {
  /* v162/v163 */
  public abstract VirtualFile getPrecompiledHeader();

  /* v162/v163 */
  public abstract OCLanguageKind getPrecompiledLanguageKind();

  /* v172 */
  public abstract String getPreprocessorDefines(OCLanguageKind kind, VirtualFile virtualFile);
}
