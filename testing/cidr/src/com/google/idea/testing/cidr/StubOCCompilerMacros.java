package com.google.idea.testing.cidr;

import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerMacros;

/** Stub {@link OCCompilerMacros} for testing. */
class StubOCCompilerMacros extends OCCompilerMacros {

  @Override
  protected void fillFileMacros(OCInclusionContext context, PsiFile sourceFile) {}
}
