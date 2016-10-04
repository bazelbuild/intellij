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

import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.intellij.lang.ASTNode;
import com.intellij.util.PlatformIcons;
import java.util.Arrays;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** PSI element for a load statement. */
public class LoadStatement extends BuildElementImpl implements Statement {

  public LoadStatement(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitLoadStatement(this);
  }

  @Nullable
  public ASTNode getImportNode() {
    return getNode().findChildByType(BuildElementTypes.STRING_LITERAL);
  }

  @Nullable
  public StringLiteral getImportPsiElement() {
    return findChildByType(BuildElementTypes.STRING_LITERAL);
  }

  @Nullable
  public String getImportedPath() {
    ASTNode firstString = getImportNode();
    return firstString != null ? StringLiteral.stripQuotes(firstString.getText()) : null;
  }

  /** The string nodes referencing imported functions. */
  public FunctionStatement[] getImportedFunctionReferences() {
    return Arrays.stream(getChildStrings())
        .skip(1)
        .map(BuildElement::getReferencedElement)
        .filter(e -> e instanceof FunctionStatement)
        .toArray(FunctionStatement[]::new);
  }

  /** The string nodes referencing imported functions. */
  public StringLiteral[] getImportedSymbolElements() {
    StringLiteral[] childStrings = getChildStrings();
    return childStrings.length < 2
        ? new StringLiteral[0]
        : Arrays.copyOfRange(childStrings, 1, childStrings.length);
  }

  public String[] getImportedSymbolNames() {
    return Arrays.stream(getImportedSymbolElements())
        .map(StringLiteral::getStringContents)
        .toArray(String[]::new);
  }

  @Nullable
  public StringLiteral findImportedSymbolElement(String name) {
    for (StringLiteral string : getImportedSymbolElements()) {
      if (name.equals(string.getStringContents())) {
        return string;
      }
    }
    return null;
  }

  public StringLiteral[] getChildStrings() {
    return Arrays.stream(getNode().getChildren(BuildElementTypes.STRINGS))
        .map(ASTNode::getPsi)
        .toArray(StringLiteral[]::new);
  }

  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.IMPORT_ICON;
  }

  @Override
  public String getPresentableText() {
    String path = LabelUtils.getNiceSkylarkFileName(getImportedPath());
    return path != null ? "load: " + path : "load";
  }
}
