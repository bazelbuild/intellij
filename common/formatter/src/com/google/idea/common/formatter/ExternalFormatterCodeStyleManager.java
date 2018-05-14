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
package com.google.idea.common.formatter;

import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * A CodeStyleManager that handles only the methods that can be processed by an external format
 * tool.
 */
public abstract class ExternalFormatterCodeStyleManager extends DelegatingCodeStyleManager {

  private static final Logger logger =
      Logger.getInstance("com.google.idea.common.formatter.ExternalFormatterCodeStyleManager");

  /**
   * A set of replacements to apply. Enforces dropping replacements that don't actually change
   * anything, avoiding unnecessary write actions and documents committals.
   */
  public static class Replacements {
    public static final Replacements EMPTY = new Replacements();
    private final Map<TextRange, String> replacements = new HashMap<>();

    public void addReplacement(TextRange range, String before, String after) {
      if (!before.equals(after)) {
        replacements.put(range, after);
      }
    }
  }

  public ExternalFormatterCodeStyleManager(CodeStyleManager delegate) {
    super(delegate);
  }

  /** Return whether or not this formatter can handle formatting the given file. */
  protected abstract boolean overrideFormatterForFile(PsiFile file);

  /**
   * Format the ranges of the given document.
   *
   * <p>Overriding methods will need to modify the document with the result of the external
   * formatter (usually using {@link #performReplacements}.
   */
  protected abstract void format(PsiFile file, Document document, Collection<TextRange> ranges);

  @Override
  public void reformatText(PsiFile file, int startOffset, int endOffset)
      throws IncorrectOperationException {
    if (overrideFormatterForFile(file)) {
      formatInternal(file, ImmutableList.of(new TextRange(startOffset, endOffset)));
    } else {
      super.reformatText(file, startOffset, endOffset);
    }
  }

  @Override
  public void reformatText(PsiFile file, Collection<TextRange> ranges)
      throws IncorrectOperationException {
    if (overrideFormatterForFile(file)) {
      formatInternal(file, ranges);
    } else {
      super.reformatText(file, ranges);
    }
  }

  @Override
  public void reformatTextWithContext(PsiFile file, Collection<TextRange> ranges) {
    if (overrideFormatterForFile(file)) {
      formatInternal(file, ranges);
    } else {
      super.reformatTextWithContext(file, ranges);
    }
  }

  @Override
  public PsiElement reformatRange(
      PsiElement element, int startOffset, int endOffset, boolean canChangeWhiteSpacesOnly) {
    // Only handle elements that are PsiFile for now -- otherwise we need to search for some
    // element within the file at new locations given the original startOffset and endOffsets
    // to serve as the return value.
    PsiFile file = element instanceof PsiFile ? (PsiFile) element : null;
    if (file != null && canChangeWhiteSpacesOnly && overrideFormatterForFile(file)) {
      formatInternal(file, ImmutableList.of(new TextRange(startOffset, endOffset)));
      return file;
    } else {
      return super.reformatRange(element, startOffset, endOffset, canChangeWhiteSpacesOnly);
    }
  }

  private void formatInternal(PsiFile file, Collection<TextRange> ranges) {
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

    format(file, document, ranges);
  }

  protected void performReplacements(Document document, Replacements replacements) {
    performReplacements(document.getText(), document, replacements);
  }

  private void performReplacements(
      String originalText, Document document, Replacements replacements) {
    if (replacements.replacements.isEmpty()) {
      return;
    }
    TreeMap<TextRange, String> sorted = new TreeMap<>(comparing(TextRange::getStartOffset));
    sorted.putAll(replacements.replacements);
    runWriteActionIfUnchanged(
        document,
        originalText,
        () -> {
          for (Entry<TextRange, String> entry : sorted.descendingMap().entrySet()) {
            document.replaceString(
                entry.getKey().getStartOffset(), entry.getKey().getEndOffset(), entry.getValue());
          }
          PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });
  }

  protected void performReplacementsAsync(
      PsiFile file, String originalText, ListenableFuture<Replacements> future) {
    future.addListener(
        () -> {
          Document document = getDocument(file);
          if (document == null || !canApplyChanges(document, originalText)) {
            return;
          }
          Replacements replacements = getFormattingFuture(future);
          if (replacements != null) {
            performReplacements(originalText, document, replacements);
          }
        },
        MoreExecutors.directExecutor());
  }

  protected void formatAsync(PsiFile file, String originalText, ListenableFuture<String> future) {
    future.addListener(
        () -> {
          Document document = getDocument(file);
          if (document == null || !canApplyChanges(document, originalText)) {
            return;
          }
          String formattedFile = getFormattingFuture(future);
          if (formattedFile == null || formattedFile.equals(getCurrentText(file))) {
            return;
          }
          runWriteActionIfUnchanged(
              document,
              originalText,
              () -> {
                document.setText(formattedFile);
                PsiDocumentManager.getInstance(getProject()).commitDocument(document);
              });
        },
        MoreExecutors.directExecutor());
  }

  /** Calls the runnable inside a write action iff the document's text hasn't changed. */
  private void runWriteActionIfUnchanged(Document document, String inputText, Runnable action) {
    WriteCommandAction.runWriteCommandAction(
        getProject(),
        () -> {
          if (inputText.equals(document.getText())) {
            action.run();
          }
        });
  }

  /**
   * Checks whether the {@link Document} is still writable, and hasn't changed since {@code
   * originalText} was calculated.
   */
  private boolean canApplyChanges(Document document, String originalText) {
    if (PsiDocumentManager.getInstance(getProject()).isDocumentBlockedByPsi(document)) {
      return false;
    }
    String currentText = document.getText();
    return originalText.equals(currentText);
  }

  @Nullable
  private static Document getDocument(PsiFile file) {
    return PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
  }

  @Nullable
  private static String getCurrentText(PsiFile file) {
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    Document document = documentManager.getDocument(file);
    return document == null ? null : document.getText();
  }

  @Nullable
  private static <V> V getFormattingFuture(ListenableFuture<V> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.warn(e);
    }
    return null;
  }
}
