package com.google.idea.sdkcompat.java;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Processor;

/** Provides SDK compatibility shims for java plugin API classes. */
public final class JavaSdkCompat {
  private JavaSdkCompat() {}

  /** #api193: wildcard generics added in 2020.1. */
  public abstract static class PsiShortNamesCacheAdapter extends PsiShortNamesCache {

    @Override
    public boolean processMethodsWithName(
        String name, GlobalSearchScope scope, Processor<PsiMethod> processor) {
      return doProcessMethodsWithName(name, scope, processor);
    }

    protected abstract boolean doProcessMethodsWithName(
        String name, GlobalSearchScope scope, Processor<? super PsiMethod> processor);
  }
}
