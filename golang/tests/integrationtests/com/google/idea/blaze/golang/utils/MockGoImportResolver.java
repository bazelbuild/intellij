package com.google.idea.blaze.golang.utils;

import com.goide.psi.impl.GoPackage;
import com.goide.psi.impl.imports.GoImportResolver;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ResolveState;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockGoImportResolver implements GoImportResolver {

  private final Map<String, GoPackage> map = new HashMap<>();


  public void put(String importPath, GoPackage goPackage) {
    map.put(importPath, goPackage);
  }

  @Override
  public @Nullable Collection<GoPackage> resolve(
      @NotNull String importPath,
      @NotNull Project project,
      @Nullable Module module,
      @Nullable ResolveState resolveState
  ) {
    GoPackage goPackage = map.get(importPath);
    return goPackage != null ? ImmutableList.of(goPackage) : null;
  }
}
