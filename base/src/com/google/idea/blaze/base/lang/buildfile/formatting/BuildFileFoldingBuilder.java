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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementTypes;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import java.util.List;
import javax.annotation.Nullable;

/** Simple code block folding for BUILD files. */
public class BuildFileFoldingBuilder implements FoldingBuilder {

  /** Currently only folding top-level nodes. */
  @Override
  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    List<FoldingDescriptor> descriptors = Lists.newArrayList();
    if (node instanceof FileASTNode) {
      for (ASTNode child : node.getChildren(null)) {
        addDescriptors(descriptors, child);
      }
    } else if (isTopLevel(node)) {
      addDescriptors(descriptors, node);
    }
    return descriptors.toArray(new FoldingDescriptor[0]);
  }

  /** Only folding top-level statements */
  private void addDescriptors(List<FoldingDescriptor> descriptors, ASTNode node) {
    IElementType type = node.getElementType();
    if (type == BuildElementTypes.FUNCTION_STATEMENT) {
      ASTNode colon = node.findChildByType(BuildToken.fromKind(TokenKind.COLON));
      if (colon == null) {
        return;
      }
      ASTNode stmtList = node.findChildByType(BuildElementTypes.STATEMENT_LIST);
      if (stmtList == null) {
        return;
      }
      int start = colon.getStartOffset() + 1;
      int end = endOfList(stmtList);
      descriptors.add(new FoldingDescriptor(node, range(start, end)));

    } else if (type == BuildElementTypes.FUNCALL_EXPRESSION
        || type == BuildElementTypes.LOAD_STATEMENT) {
      ASTNode listNode =
          type == BuildElementTypes.FUNCALL_EXPRESSION
              ? node.findChildByType(BuildElementTypes.ARGUMENT_LIST)
              : node;
      if (listNode == null) {
        return;
      }
      ASTNode lParen = listNode.findChildByType(BuildToken.fromKind(TokenKind.LPAREN));
      ASTNode rParen = listNode.findChildByType(BuildToken.fromKind(TokenKind.RPAREN));
      if (lParen == null || rParen == null) {
        return;
      }
      int start = lParen.getStartOffset() + 1;
      int end = rParen.getTextRange().getEndOffset() - 1;
      descriptors.add(new FoldingDescriptor(node, range(start, end)));
    }
  }

  private static TextRange range(int start, int end) {
    if (start >= end) {
      return new TextRange(start, start + 1);
    }
    return new TextRange(start, end);
  }

  /**
   * Don't include whitespace and newlines at the end of the function.<br>
   * Could do this in the lexer instead, with additional look-ahead checks.
   */
  private int endOfList(ASTNode stmtList) {
    ASTNode child = stmtList.getLastChildNode();
    while (child != null) {
      IElementType type = child.getElementType();
      if (type != TokenType.WHITE_SPACE && type != BuildToken.fromKind(TokenKind.NEWLINE)) {
        return child.getTextRange().getEndOffset();
      }
      child = child.getTreePrev();
    }
    return stmtList.getTextRange().getEndOffset();
  }

  private boolean isTopLevel(ASTNode node) {
    return node.getTreeParent() instanceof FileASTNode;
  }

  @Override
  @Nullable
  public String getPlaceholderText(ASTNode node) {
    PsiElement psi = node.getPsi();
    if (psi instanceof FuncallExpression) {
      FuncallExpression expr = (FuncallExpression) psi;
      String name = expr.getNameArgumentValue();
      if (name != null) {
        return "name = \"" + name + "\"...";
      }
    }
    if (psi instanceof LoadStatement) {
      String fileName = ((LoadStatement) psi).getImportedPath();
      if (fileName != null) {
        return "\"" + fileName + "\"...";
      }
    }
    return "...";
  }

  @Override
  public boolean isCollapsedByDefault(ASTNode node) {
    return false;
  }
}
