/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ExternalWorkspaceLookupElement extends BuildLookupElement {
  private final ExternalWorkspace workspace;

  public ExternalWorkspaceLookupElement(ExternalWorkspace workspace) {
    super('@' + workspace.repoName(), QuoteType.NoQuotes);
    this.workspace = workspace;
  }

  @Override
  public String getLookupString() {
    return super.getItemText();
  }

  @Override
  @Nullable
  protected String getTypeText() {
    return !workspace.repoName().equals(workspace.name()) ? '@' + workspace.name() : null;
  }

  @Override
  @Nullable
  protected String getTailText() {
    return "//";
  }

  @Override
  public @Nullable Icon getIcon() {
    return PlatformIcons.LIBRARY_ICON;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    StringLiteral literal = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), StringLiteral.class, false);
    if (literal == null) {
      super.handleInsert(context);
      return;
    }

    Document document = context.getDocument();
    context.commitDocument();

    // if we completed by pressing '/' (since this lookup element should never complete using '/') .
    if (context.getCompletionChar() == '/') {
      context.setAddCompletionChar(false);
    }

    CaretModel caret = context.getEditor().getCaretModel();
        // find an remove trailing package path after insert / replace.
    //  current element text looks like `@workspace`. If this is complete inside an existing workspace name the
    //  result would look like: @workspace<caret>old_workspace_path//. The following bit will remove `old_workspace_path//`
    int replaceStart = context.getTailOffset();
    int replaceEnd = context.getTailOffset();

    int indexOfPackageStart = literal.getText().lastIndexOf("//");
    if (indexOfPackageStart != -1) {
      // shift to be a document offset
      replaceEnd = indexOfPackageStart + 2 + literal.getTextOffset();
    }

    document.replaceString(replaceStart, replaceEnd, "//");
    context.commitDocument();
    caret.moveToOffset(replaceStart + 2);

    // handle auto insertion of end quote. This will have to be if the completion is triggered inside a `"@<caret` bit
    if (insertClosingQuotes()) {
      QuoteType quoteType = literal.getQuoteType();
      if (quoteType != QuoteType.NoQuotes && !literal.getText().endsWith(quoteType.quoteString)) {
        document.insertString(literal.getTextOffset() + literal.getTextLength(), quoteType.quoteString);
        context.commitDocument();
      }
    }

    super.handleInsert(context);
  }

  @Override
  public boolean requiresCommittedDocuments() {
    return false;
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE;
  }
}
