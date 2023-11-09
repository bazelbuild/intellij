package com.google.idea.blaze.base.lang.buildfile.references;

import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

public class VisibilityReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
        VisibilityReferenceProvider.PATTERN, new VisibilityReferenceProvider());
  }
}
