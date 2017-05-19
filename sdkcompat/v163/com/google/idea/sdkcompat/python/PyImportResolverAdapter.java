package com.google.idea.sdkcompat.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.resolve.QualifiedNameResolveContext;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public interface PyImportResolverAdapter extends PyImportResolver {

  @Nullable
  PsiElement resolveImportReference(
      QualifiedName name, PyQualifiedNameResolveContextAdapter context, boolean withRoots);

  @Override
  @Nullable
  default PsiElement resolveImportReference(
      QualifiedName name, QualifiedNameResolveContext context, boolean withRoots) {
    return resolveImportReference(
        name, new PyQualifiedNameResolveContextAdapter(context), withRoots);
  }
}
