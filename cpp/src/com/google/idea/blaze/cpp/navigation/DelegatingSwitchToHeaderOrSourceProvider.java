/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp.navigation;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.cidr.lang.navigation.OCSwitchToHeaderOrSourceRelatedProvider;
import com.jetbrains.cidr.lang.psi.OCFile;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Wrapper around {@link OCSwitchToHeaderOrSourceRelatedProvider} to work around 20+ second freezes
 * when symbol resolution needs to be recomputed (cache invalidated). Seems especially bad for
 * *_test.cc files (which currently churn through lots of headers).
 *
 * <p>Remove this workaround once https://youtrack.jetbrains.com/issue/CPP-7168, or
 * https://youtrack.jetbrains.com/issue/CPP-8461 are fixed.
 */
public class DelegatingSwitchToHeaderOrSourceProvider extends GotoRelatedProvider {

  private static final long TIMEOUT_MS = 5000;
  private static final GotoRelatedProvider DELEGATE = new OCSwitchToHeaderOrSourceRelatedProvider();
  private static final Logger logger =
      Logger.getInstance(DelegatingSwitchToHeaderOrSourceProvider.class);
  private static final ImmutableList<String> TEST_SUFFIXES =
      ImmutableList.of("_test", "-test", "_unittest", "-unittest");

  @Override
  public List<? extends GotoRelatedItem> getItems(PsiElement psiElement) {
    PsiFile psiFile = psiElement.getContainingFile();
    Project project = psiElement.getProject();
    if (!(psiFile instanceof OCFile) || !Blaze.isBlazeProject(project)) {
      return ImmutableList.of();
    }
    Optional<List<? extends GotoRelatedItem>> fromDelegate =
        getItemsWithTimeout(() -> DELEGATE.getItems(psiElement));
    if (!fromDelegate.isPresent()) {
      logger.info("Timed out: Trying fallback for .h <-> .cc");
      return getItemsFallback((OCFile) psiFile);
    }
    return fromDelegate.get();
  }

  @Override
  public List<? extends GotoRelatedItem> getItems(DataContext context) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(context);
    if (!(psiFile instanceof OCFile) || project == null || !Blaze.isBlazeProject(project)) {
      return ImmutableList.of();
    }
    Optional<List<? extends GotoRelatedItem>> fromDelegate =
        getItemsWithTimeout(() -> DELEGATE.getItems(context));
    if (!fromDelegate.isPresent()) {
      logger.info("Timed out: Trying fallback for .h <-> .cc");
      return getItemsFallback((OCFile) psiFile);
    }
    return fromDelegate.get();
  }

  /**
   * Runs the getter under a progress indicator that cancels itself after a certain timeout (assumes
   * that the getter will check for cancellation cooperatively).
   *
   * @param getter computes the GotoRelatedItems.
   * @return a list of items computed, or Optional.empty if timed out.
   */
  private Optional<List<? extends GotoRelatedItem>> getItemsWithTimeout(
      ThrowableComputable<List<? extends GotoRelatedItem>, RuntimeException> getter) {
    try {
      ProgressIndicator indicator = new ProgressIndicatorBase();
      ProgressIndicator wrappedIndicator =
          new WatchdogIndicator(indicator, TIMEOUT_MS, TimeUnit.MILLISECONDS);
      // We don't use "runProcessWithProgressSynchronously" because that pops up a ProgressWindow,
      // and that will cause the event IDs to bump up and no longer match the event ID stored in
      // DataContexts which may be used in one of the GotoRelatedProvider#getItems overloads.
      return Optional.of(
          ProgressManager.getInstance()
              .runProcess(() -> ReadAction.compute(getter), wrappedIndicator));
    } catch (ProcessCanceledException e) {
      return Optional.empty();
    }
  }

  private List<? extends GotoRelatedItem> getItemsFallback(OCFile file) {
    OCFile target = file.getAssociatedFileWithSameName();
    if (target == null && !file.isHeader() && file.getVirtualFile() != null) {
      target = correlateTestToHeader(file);
    }
    if (target == null) {
      return ImmutableList.of();
    }
    return ImmutableList.of(new GotoRelatedItem(target, target.isHeader() ? "Headers" : "Sources"));
  }

  @Nullable
  private OCFile correlateTestToHeader(OCFile file) {
    // Quickly check foo_test.cc -> foo.h as well. "getAssociatedFileWithSameName" only does
    // foo.cc <-> foo.h. However, if you do goto-related-symbol again, it will go from
    // foo.h -> foo.cc instead of back to foo_test.cc.
    PsiManager psiManager = PsiManager.getInstance(file.getProject());
    String pathWithoutExtension = FileUtil.getNameWithoutExtension(file.getVirtualFile().getPath());
    for (String testSuffix : TEST_SUFFIXES) {
      if (pathWithoutExtension.endsWith(testSuffix)) {
        String possibleHeaderName = StringUtil.trimEnd(pathWithoutExtension, testSuffix) + ".h";
        VirtualFile virtualFile = VfsUtil.findFileByIoFile(new File(possibleHeaderName), false);
        if (virtualFile != null) {
          PsiFile psiFile = psiManager.findFile(virtualFile);
          if (psiFile instanceof OCFile) {
            return (OCFile) psiFile;
          }
        }
      }
    }
    return null;
  }

  /**
   * IntelliJ loops through all GotoRelatedProvider, rather than stop on the first so we need to
   * unregister the delegate to wrap it.
   */
  public static class ExtensionReplacer implements ApplicationComponent {
    @Override
    public void initComponent() {
      ExtensionPoint<GotoRelatedProvider> ep =
          Extensions.getRootArea().getExtensionPoint(GotoRelatedProvider.EP_NAME);
      for (GotoRelatedProvider provider : ep.getExtensions()) {
        if (provider.getClass().equals(OCSwitchToHeaderOrSourceRelatedProvider.class)) {
          ep.unregisterExtension(provider);
        }
      }
    }

    @Override
    public void disposeComponent() {}

    @Override
    public String getComponentName() {
      return ExtensionReplacer.class.getName();
    }
  }
}
