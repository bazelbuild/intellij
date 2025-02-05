package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class DelegatingTypeScriptConfigServiceCompat implements TypeScriptConfigService {

  protected final TypeScriptConfigService impl;

  protected DelegatingTypeScriptConfigServiceCompat(TypeScriptConfigService impl) {
    this.impl = impl;
  }

  @Override
  public IntPredicate getFilterId(VirtualFile scope, boolean useProjectScopeGraph) {
    return impl.getFilterId(scope, useProjectScopeGraph);
  }
}
