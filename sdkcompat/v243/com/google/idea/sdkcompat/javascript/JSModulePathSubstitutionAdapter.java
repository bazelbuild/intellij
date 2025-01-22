package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution;

import java.util.Collection;

public abstract class JSModulePathSubstitutionAdapter implements JSModulePathSubstitution {

  public abstract Collection<String> getMappingsAsStrings();

  @Override
  public Collection<String> getMappings() {
    return getMappingsAsStrings();
  }
}