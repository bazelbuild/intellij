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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.references.LabelReference;
import com.google.idea.blaze.base.lang.buildfile.references.LoadedSymbolReference;
import com.google.idea.blaze.base.lang.buildfile.references.PackageReferenceFragment;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

import javax.annotation.Nullable;

/**
 * PSI node for string literal expressions
 */
public class StringLiteral extends BuildElementImpl implements LiteralExpression {

  public static String stripEndpointQuotes(ASTNode node) {
    assert(node.getElementType() == BuildElementTypes.STRING_LITERAL);
    return parseStringContents(node.getText());
  }

  /**
   * Removes the leading and trailing quotes. Naive implementation intended for resolving references
   * (in which case escaped characters, raw strings, etc. are unlikely).
   */
  public static String parseStringContents(String string) {
    // TODO: Handle escaped characters, etc. here? (extract logic from BuildLexerBase.addStringLiteral)
    if (string.startsWith("\"\"\"") || string.startsWith("'''")) {
      return string.length() < 6 ? "" : string.substring(3, string.length() - 3);
    }
    return string.length() < 2 ? "" : string.substring(1, string.length() - 1);
  }

  public static QuoteType getQuoteType(@Nullable String rawText) {
    if (rawText == null) {
      return QuoteType.NoQuotes;
    }
    if (rawText.startsWith("\"\"\"")) {
      return QuoteType.TripleDouble;
    }
    if (rawText.startsWith("'''")) {
      return QuoteType.TripleSingle;
    }
    if (rawText.startsWith("'")) {
      return QuoteType.Single;
    }
    if (rawText.startsWith("\"")) {
      return QuoteType.Double;
    }
    return QuoteType.NoQuotes;
  }

  public StringLiteral(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitStringLiteral(this);
  }

  /**
   * Removes the leading and trailing quotes
   */
  public String getStringContents() {
    return parseStringContents(getText());
  }

  public QuoteType getQuoteType() {
    return getQuoteType(getText());
  }

  /**
   * Labels are taken to reference:
   *   - the actual target they reference
   *   - the BUILD package specified before the colon (only if explicitly present)
   */
  @Override
  public PsiReference[] getReferences() {
    PsiReference primaryReference = getReference();
    if (primaryReference instanceof LabelReference) {
      return new PsiReference[] {primaryReference, new PackageReferenceFragment((LabelReference) primaryReference)};
    }
    return primaryReference != null ? new PsiReference[] {primaryReference} : PsiReference.EMPTY_ARRAY;
  }

  /**
   * The primary reference -- this is the target referenced by the full label
   */
  @Nullable
  @Override
  public PsiReference getReference() {
    PsiElement parent = getParent();
    if (parent instanceof LoadStatement) {
      LoadStatement load = (LoadStatement) parent;
      StringLiteral importNode = load.getImportPsiElement();
      if (importNode == null) {
        return null;
      }
      LabelReference importReference = new LabelReference(importNode, false);
      if (this.equals(importNode)) {
        return importReference;
      }
      return new LoadedSymbolReference(this, importReference);
    }
    return new LabelReference(this, true);
  }

  public boolean insideLoadStatement() {
    return getParentType() == BuildElementTypes.LOAD_STATEMENT;
  }

  @Override
  public String getPresentableText() {
    return getText();
  }

}
