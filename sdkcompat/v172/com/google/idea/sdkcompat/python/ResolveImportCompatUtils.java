package com.google.idea.sdkcompat.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import javax.annotation.Nullable;

/** Handles changes to {@link ResolveImportUtil} between our supported versions. */
public class ResolveImportCompatUtils {

  @Nullable
  public static PsiElement resolveChild(
      @Nullable PsiElement parent,
      String referencedName,
      @Nullable PsiFile containingFile,
      boolean fileOnly,
      boolean checkForPackage,
      boolean withoutStubs) {
    return ResolveImportUtil.resolveChild(
        parent, referencedName, containingFile, fileOnly, checkForPackage, withoutStubs);
  }
}
