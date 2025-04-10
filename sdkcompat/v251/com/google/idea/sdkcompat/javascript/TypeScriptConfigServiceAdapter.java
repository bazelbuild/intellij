package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class TypeScriptConfigServiceAdapter implements TypeScriptConfigService {

  @Override
  public IntPredicate getFilterId(VirtualFile scope, boolean useProjectScopeGraph) {
    return i -> true;
  }

  @Override
  public @NotNull IntPredicate getFilterId(@NotNull VirtualFile virtualFile) {
    return i -> true;
  }
}
