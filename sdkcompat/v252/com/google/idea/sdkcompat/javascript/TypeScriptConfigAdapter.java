package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.config.JSModuleResolution;
import com.intellij.lang.javascript.config.JSModuleTarget;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;

// #api252
public abstract class TypeScriptConfigAdapter implements TypeScriptConfig {

  public abstract JSModuleResolution getModuleResolution();

  public abstract JSModuleResolution getEffectiveModuleResolution();
}