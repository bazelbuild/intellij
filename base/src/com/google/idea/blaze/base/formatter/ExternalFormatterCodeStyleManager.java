/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.formatter;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.formatter.FileBasedFormattingSynchronizer.Formatter;
import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.google.idea.sdkcompat.formatter.DelegatingCodeStyleManagerCompat;
import com.google.idea.sdkcompat.formatter.ExternalFormatterCodeStyleManagerAdapter;
import com.intellij.formatting.FormattingMode;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.FormattingModeAwareIndentAdjuster;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A CodeStyleManager that handles only the methods that can be processed by an external formatting
 * tool.
 */
class ExternalFormatterCodeStyleManager extends ExternalFormatterCodeStyleManagerAdapter {

  public ExternalFormatterCodeStyleManager(CodeStyleManager delegate) {
    super(delegate);
  }

  @Override
  public int adjustLineIndent(final Document document, final int offset, FormattingMode mode)
      throws IncorrectOperationException {
    if (delegate instanceof FormattingModeAwareIndentAdjuster) {
      return ((FormattingModeAwareIndentAdjuster) delegate)
          .adjustLineIndent(document, offset, mode);
    }
    return offset;
  }

  @Override
  public FormattingMode getCurrentFormattingMode() {
    if (delegate instanceof FormattingModeAwareIndentAdjuster) {
      return ((FormattingModeAwareIndentAdjuster) delegate).getCurrentFormattingMode();
    }
    return FormattingMode.REFORMAT;
  }

  static class Installer implements StartupActivity {
    @Override
    public void runActivity(Project project) {
      FormatterInstaller.replaceFormatter(project, ExternalFormatterCodeStyleManager::new);
    }
  }

  @Nullable
  protected CustomFormatter getCustomFormatterForFile(PsiFile file) {
    return CustomFormatter.EP_NAME
        .extensions()
        .filter(e -> e.appliesToFile(getProject(), file))
        .findFirst()
        .orElse(null);
  }

  @Override
  public Project getProject() {
    return delegate.getProject();
  }

  @Override
  public PsiElement reformat(PsiElement element) throws IncorrectOperationException {
    return delegate.reformat(element);
  }

  @Override
  public PsiElement reformat(PsiElement element, boolean canChangeWhiteSpacesOnly)
      throws IncorrectOperationException {
    return delegate.reformat(element, canChangeWhiteSpacesOnly);
  }

  @Override
  public PsiElement reformatRange(PsiElement element, int startOffset, int endOffset)
      throws IncorrectOperationException {
    return delegate.reformatRange(element, startOffset, endOffset);
  }

  @Override
  public PsiElement reformatRange(
      PsiElement element, int startOffset, int endOffset, boolean canChangeWhiteSpacesOnly)
      throws IncorrectOperationException {
    return delegate.reformatRange(element, startOffset, endOffset, canChangeWhiteSpacesOnly);
  }

  @Override
  public void reformatText(PsiFile file, int startOffset, int endOffset)
      throws IncorrectOperationException {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ImmutableList.of(new TextRange(startOffset, endOffset)));
    } else {
      delegate.reformatText(file, startOffset, endOffset);
    }
  }

  @Override
  public void reformatTextLegacy(PsiFile file, Collection<TextRange> ranges)
      throws IncorrectOperationException {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ranges);
    } else {
      delegate.reformatText(file, ranges);
    }
  }

  @Override
  public void reformatTextAdapted(PsiFile file, Collection<? extends TextRange> ranges)
      throws IncorrectOperationException {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ranges);
    } else {
      super.reformatTextFromDelegate(file, ranges);
    }
  }

  @Override
  public void reformatTextWithContextLegacy(PsiFile file, Collection<TextRange> ranges) {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ranges);
    } else {
      delegate.reformatTextWithContext(file, ranges);
    }
  }

  @Override
  public void reformatTextWithContextAdapted(PsiFile file, Collection<? extends TextRange> ranges) {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter != null) {
      formatInternal(formatter, file, ranges);
    } else {
      reformatTextWithContextFromDelegate(file, ranges);
    }
  }

  @Override
  public void reformatTextWithContext(PsiFile file, ChangedRangesInfo info) {
    CustomFormatter formatter = getCustomFormatterForFile(file);
    if (formatter == null) {
      delegate.reformatTextWithContext(file, info);
      return;
    }

    if (formatter.alwaysFormatEntireFile()) {
      this.reformatText(file, 0, file.getTextLength());
    } else {
      formatInternal(formatter, file, info);
    }
  }

  @Override
  public void adjustLineIndent(PsiFile file, TextRange rangeToAdjust)
      throws IncorrectOperationException {
    delegate.adjustLineIndent(file, rangeToAdjust);
  }

  @Override
  public int adjustLineIndent(PsiFile file, int offset) throws IncorrectOperationException {
    return delegate.adjustLineIndent(file, offset);
  }

  @Override
  public int adjustLineIndent(Document document, int offset) {
    return delegate.adjustLineIndent(document, offset);
  }

  @Override
  public boolean isLineToBeIndented(PsiFile file, int offset) {
    return delegate.isLineToBeIndented(file, offset);
  }

  @Override
  @Nullable
  public String getLineIndent(PsiFile file, int offset) {
    return delegate.getLineIndent(file, offset);
  }

  @Override
  @Nullable
  public String getLineIndent(Document document, int offset) {
    return delegate.getLineIndent(document, offset);
  }

  @Override
  public Indent getIndent(String text, FileType fileType) {
    return delegate.getIndent(text, fileType);
  }

  @Override
  public String fillIndent(Indent indent, FileType fileType) {
    return delegate.fillIndent(indent, fileType);
  }

  @Override
  public Indent zeroIndent() {
    return delegate.zeroIndent();
  }

  @Override
  public void reformatNewlyAddedElement(ASTNode block, ASTNode addedElement)
      throws IncorrectOperationException {
    delegate.reformatNewlyAddedElement(block, addedElement);
  }

  @Override
  public boolean isSequentialProcessingAllowed() {
    return delegate.isSequentialProcessingAllowed();
  }

  @Override
  public void performActionWithFormatterDisabled(Runnable r) {
    delegate.performActionWithFormatterDisabled(r);
  }


  @Override
  public <T extends Throwable> void performActionWithFormatterDisabled(ThrowableRunnable<T> r)
      throws T {
    delegate.performActionWithFormatterDisabled(r);
  }

  @Override
  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    return delegate.performActionWithFormatterDisabled(r);
  }

  // #api201: Method introduced in 2020.2. If not overridden, an exception is thrown upon class
  // creation.
  @SuppressWarnings("override")
  public void scheduleReformatWhenSettingsComputed(PsiFile file) {
    DelegatingCodeStyleManagerCompat.scheduleReformatWhenSettingsComputed(delegate, file);
  }

  private void formatInternal(CustomFormatter formatter, PsiFile file, ChangedRangesInfo info) {
    List<TextRange> ranges = new ArrayList<>();
    if (info.insertedRanges != null) {
      ranges.addAll(info.insertedRanges);
    }
    ranges.addAll(info.allChangedRanges);
    formatInternal(formatter, file, ranges);
  }

  protected void formatInternal(
      CustomFormatter formatter, PsiFile file, Collection<? extends TextRange> ranges) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    documentManager.commitAllDocuments();
    CheckUtil.checkWritable(file);

    Document document = documentManager.getDocument(file);

    if (document == null) {
      return;
    }
    // If there are postponed PSI changes (e.g., during a refactoring), just abort.
    // If we apply them now, then the incoming text ranges may no longer be valid.
    if (documentManager.isDocumentBlockedByPsi(document)) {
      return;
    }
    FileContentsProvider fileContents = FileContentsProvider.fromPsiFile(file);
    if (fileContents == null) {
      return;
    }
    ListenableFuture<Void> future =
        FileBasedFormattingSynchronizer.applyReplacements(
            file,
            f -> {
              Replacements replacements =
                  formatter.getReplacements(getProject(), fileContents, ranges);
              return new Formatter.Result<>(null, replacements);
            });
    FormatUtils.formatWithProgressDialog(file.getProject(), formatter.progressMessage(), future);
  }
}
