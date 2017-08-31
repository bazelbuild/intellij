package com.google.idea.sdkcompat.ui;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/** SDK adapter for {@link BreadcrumbsInfoProvider}, deprecated in 172. */
public abstract class BreadcrumbsProviderSdkCompatAdapter extends BreadcrumbsInfoProvider {

  public static BreadcrumbsProviderSdkCompatAdapter[] getBreadcrumbsProviders() {
    return Arrays.stream(BreadcrumbsInfoProvider.EP_NAME.getExtensions())
        .map(BreadcrumbsProviderSdkCompatAdapter::fromBreadcrumbsProvider)
        .toArray(BreadcrumbsProviderSdkCompatAdapter[]::new);
  }

  private static BreadcrumbsProviderSdkCompatAdapter fromBreadcrumbsProvider(
      BreadcrumbsInfoProvider delegate) {
    return new BreadcrumbsProviderSdkCompatAdapter() {

      @Override
      public Language[] getLanguages() {
        return delegate.getLanguages();
      }

      @Override
      public boolean acceptElement(PsiElement psiElement) {
        return delegate.acceptElement(psiElement);
      }

      @Override
      public String getElementInfo(PsiElement psiElement) {
        return delegate.getElementInfo(psiElement);
      }

      @Nullable
      @Override
      public String getElementTooltip(PsiElement element) {
        return delegate.getElementTooltip(element);
      }

      @Nullable
      @Override
      public PsiElement getParent(PsiElement element) {
        return delegate.getParent(element);
      }
    };
  }
}
