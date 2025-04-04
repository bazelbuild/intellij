package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class TypeScriptConfigServiceAdapter implements TypeScriptConfigService {

  @Override
  public IntPredicate getFilterId(VirtualFile scope) {
    return i -> true;
  }
}
