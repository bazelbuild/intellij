package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;

import java.util.Collection;

public abstract class TypeScriptConfigAdapter implements TypeScriptConfig {

  public abstract Collection<String> getMappingsAsStrings();

  public abstract String getPattern();

  public abstract boolean canStartWith();
}