package com.google.idea.sdkcompat.javascript;

import com.google.common.collect.ImmutableList;
import com.intellij.lang.javascript.frameworks.modules.JSModuleMapping;
import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution;

import java.util.Collection;

public abstract class JSModulePathSubstitutionAdapter implements JSModulePathSubstitution {

  public abstract Collection<String> getMappingsAsStrings();

  @Override
  public Collection<JSModuleMapping> getMappings() {
    return getMappingsAsStrings().stream().distinct().map(JSModuleMapping::new).collect(ImmutableList.toImmutableList());
  }
}