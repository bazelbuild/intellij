package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Adapter to bridge different SDK versions. */
public abstract class OCResolveConfigurationAdapter extends UserDataHolderBase
    implements OCResolveConfiguration {
  /* v171 */
  public abstract List<VirtualFile> getPrecompiledHeaders(
      OCLanguageKind kind, VirtualFile sourceFile);

  /* v171 */
  public abstract Collection<VirtualFile> getSources();

  /* v171 */
  public abstract Set<VirtualFile> getPrecompiledHeaders();

  /* v172 */
  public abstract String getPreprocessorDefines(OCLanguageKind kind, VirtualFile virtualFile);
}
