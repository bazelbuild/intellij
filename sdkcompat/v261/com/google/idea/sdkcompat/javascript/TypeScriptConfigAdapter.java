package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.config.JSModuleResolution;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import org.jetbrains.annotations.NotNull;

// #api252
public abstract class TypeScriptConfigAdapter implements TypeScriptConfig {

  @Override
  public @NotNull JSModuleResolution getEffectiveModuleResolution() {
    return getEffectiveResolution();
  }

  @Override
  public @NotNull JSModuleResolution getModuleResolution() {
    return getResolution();
  }
}