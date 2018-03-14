/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.ui;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.Nullable;

/** SDK adapter for {@link BreadcrumbsProvider}, added in 172. */
public abstract class BreadcrumbsProviderSdkCompatAdapter implements BreadcrumbsProvider {

  public static BreadcrumbsProviderSdkCompatAdapter[] getBreadcrumbsProviders() {
    return Arrays.stream(BreadcrumbsProvider.EP_NAME.getExtensions())
        .map(BreadcrumbsProviderSdkCompatAdapter::fromBreadcrumbsProvider)
        .toArray(BreadcrumbsProviderSdkCompatAdapter[]::new);
  }

  private static BreadcrumbsProviderSdkCompatAdapter fromBreadcrumbsProvider(
      BreadcrumbsProvider delegate) {
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
      public Icon getElementIcon(PsiElement element) {
        return delegate.getElementIcon(element);
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

      @Override
      public List<PsiElement> getChildren(PsiElement element) {
        return delegate.getChildren(element);
      }
    };
  }
}
