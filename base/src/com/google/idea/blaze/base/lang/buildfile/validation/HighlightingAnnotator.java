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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.idea.blaze.base.lang.buildfile.highlighting.BuildSyntaxHighlighter;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuiltInNamesProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/** Additional syntax highlighting, based on parsed PSI elements. */
public class HighlightingAnnotator extends BuildAnnotator {

  @Override
  public void visitParameter(Parameter node) {
    FunctionStatement function = PsiTreeUtil.getParentOfType(node, FunctionStatement.class);
    if (function != null) {
      PsiElement anchor = node.hasDefaultValue() ? node.getFirstChild() : node;
      final Annotation annotation = getHolder().createInfoAnnotation(anchor, null);
      annotation.setTextAttributes(BuildSyntaxHighlighter.BUILD_PARAMETER);
    }
  }

  @Override
  public void visitKeywordArgument(Argument.Keyword node) {
    ASTNode keywordNode = node.getNameNode();
    if (keywordNode != null) {
      Annotation annotation = getHolder().createInfoAnnotation(keywordNode, null);
      annotation.setTextAttributes(BuildSyntaxHighlighter.BUILD_KEYWORD_ARG);
    }
  }

  @Override
  public void visitFunctionStatement(FunctionStatement node) {
    ASTNode nameNode = node.getNameNode();
    if (nameNode != null) {
      Annotation annotation = getHolder().createInfoAnnotation(nameNode, null);
      annotation.setTextAttributes(BuildSyntaxHighlighter.BUILD_FN_DEFINITION);
    }
  }

  @Override
  public void visitReferenceExpression(ReferenceExpression node) {
    ASTNode nameNode = node.getNameElement();
    if (nameNode != null
        && BuiltInNamesProvider.getBuiltInNames(node.getProject()).contains(nameNode.getText())) {
      Annotation annotation = getHolder().createInfoAnnotation(nameNode, null);
      annotation.setTextAttributes(BuildSyntaxHighlighter.BUILD_BUILTIN_NAME);
    }
    super.visitReferenceExpression(node);
  }
}
