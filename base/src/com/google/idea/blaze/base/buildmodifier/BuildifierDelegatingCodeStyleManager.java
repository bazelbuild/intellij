/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.buildmodifier;

import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.common.formatter.DelegatingCodeStyleManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.util.IncorrectOperationException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link CodeStyleManager} implementation which runs buildifier on BUILD files, and otherwise
 * delegates to the existing formatter.
 */
public class BuildifierDelegatingCodeStyleManager extends DelegatingCodeStyleManager {

  public BuildifierDelegatingCodeStyleManager(CodeStyleManager original) {
    super(original);
  }

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
  public void reformatTextWithContext(PsiFile file, Collection<TextRange> ranges)
      throws IncorrectOperationException {
    if (overrideFormatterForFile(file)) {
      formatInternal(file, ranges);
    } else {
      super.reformatTextWithContext(file, ranges);
    }
  }

  private static boolean overrideFormatterForFile(PsiFile file) {
    // don't format skylark extensions
    return file instanceof BuildFile
        && ((BuildFile) file).getBlazeFileType() == BlazeFileType.BuildPackage;
  }

  private void formatInternal(PsiFile file, Collection<TextRange> ranges) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    CheckUtil.checkWritable(file);

    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    if (document == null) {
      return;
    }
    Map<TextRange, String> replacements = getFormatReplacements(document.getText(), ranges);
    TreeMap<TextRange, String> sortedReplacements =
        new TreeMap<>(comparing(TextRange::getStartOffset));
    sortedReplacements.putAll(replacements);
    performReplacements(document, sortedReplacements);
  }

  private static Map<TextRange, String> getFormatReplacements(
      String text, Collection<TextRange> ranges) {
    ImmutableMap.Builder<TextRange, String> output = ImmutableMap.builder();
    for (TextRange range : ranges) {
      String result = BuildFileFormatter.formatText(range.substring(text));
      if (result == null) {
        return ImmutableMap.of();
      }
      output.put(range, result);
    }
    return output.build();
  }

  private void performReplacements(
      final Document document, final Map<TextRange, String> reverseSortedReplacements) {
    WriteCommandAction.runWriteCommandAction(
        getProject(),
        () -> {
          for (Map.Entry<TextRange, String> replacement : reverseSortedReplacements.entrySet()) {
            TextRange range = replacement.getKey();
            document.replaceString(
                range.getStartOffset(), range.getEndOffset(), replacement.getValue());
          }
          PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });
  }
}
