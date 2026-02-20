/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

import javax.annotation.Nullable;
import javax.swing.*;

/** PSI element for an include statement in MODULE.bazel files. */
public class IncludeStatement extends BuildElementImpl implements Statement {

  public IncludeStatement(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitIncludeStatement(this);
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
  public String getIncludedPath() {
    ASTNode firstString = getImportNode();
    return firstString != null ? StringLiteral.stripQuotes(firstString.getText()) : null;
  }

  @Override
  public Icon getIcon(int flags) {
    return PlatformIcons.IMPORT_ICON;
  }

  @Override
  public String getPresentableText() {
    return "include";
  }

  @Nullable
  @Override
  public String getLocationString() {
    return LabelUtils.getNiceSkylarkFileName(getIncludedPath());
  }
}
