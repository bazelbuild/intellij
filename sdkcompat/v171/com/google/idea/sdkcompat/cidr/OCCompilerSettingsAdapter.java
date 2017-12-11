package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.compiler.CidrCompilerResult;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.CompilerInfoCache.Entry;

/** Adapter to bridge different SDK versions. */
public abstract class OCCompilerSettingsAdapter extends OCCompilerSettings {
  public abstract CidrCompilerResult<Entry> getCompilerInfo(
      OCLanguageKind ocLanguageKind, VirtualFile virtualFile);
}
