package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;
import java.util.Map;

/** Adapter to bridge different SDK versions. */
public abstract class OCCompilerMacrosAdapter extends OCCompilerMacros {
  // v171
  public void addAllFeatures(Map<String, String> result, Map<String, String> features) {
    result.putAll(features);
  }
  // v172
  public abstract String getAllDefines(OCLanguageKind kind, VirtualFile vf);
}
