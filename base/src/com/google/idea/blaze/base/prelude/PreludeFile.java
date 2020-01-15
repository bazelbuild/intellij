package com.google.idea.blaze.base.prelude;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;

import javax.annotation.Nullable;

final public class PreludeFile {

  private final BuildFile delegate;

  PreludeFile(BuildFile delegate) {
    this.delegate = delegate;
  }

  public boolean searchSymbolsInScope(Processor<BuildElement> processor,
      @Nullable PsiElement stopAtElement) {
    return delegate.searchSymbolsInScope(processor, stopAtElement);
  }

}
