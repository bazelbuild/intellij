package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Adapter to bridge different SDK versions. */
public interface OCResolveConfigurationAdapter extends OCResolveConfiguration {
  /* v171 */
  public List<VirtualFile> getPrecompiledHeaders(OCLanguageKind kind, VirtualFile sourceFile);

  /* v171 */
  public Collection<VirtualFile> getSources();

  /* v171 */
  public Set<VirtualFile> getPrecompiledHeaders();
}
