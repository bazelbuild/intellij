package com.google.idea.blaze.base.lang.projectview.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProjectViewFoldingBuilder extends CustomFoldingBuilder {

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> list, @NotNull PsiElement psiElement, @NotNull Document document, boolean quick) {
  }

  @Override
  protected String getLanguagePlaceholderText(@NotNull ASTNode astNode, @NotNull TextRange textRange) {
    return "# ...";
  }

  @Override
  protected boolean isRegionCollapsedByDefault(@NotNull ASTNode astNode) {
    return false;
  }


}
