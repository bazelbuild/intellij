package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Creates an icon for {@link FuncallExpression}s in Blaze Build Files */
public class BlazeBuildFileRunLineMarkerProvider extends RunLineMarkerContributor {

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement psiElement) {
    if (psiElement instanceof LeafPsiElement) {
      LeafPsiElement leaf = (LeafPsiElement) psiElement;
      if (leaf.getElementType() instanceof BuildToken) {
        BuildToken buildToken = (BuildToken) leaf.getElementType();
        if (leaf.getPrevSibling() == null
            && leaf.getNextSibling() == null
            && buildToken.kind == TokenKind.IDENTIFIER) {
          FuncallExpression rule = PsiTreeUtil
              .getNonStrictParentOfType(psiElement, FuncallExpression.class);
          if (rule != null) {
            String ruleType = rule.getFunctionName();
            Label label = rule.resolveBuildLabel();
            if (ruleType != null && label != null) {
              return new Info(
                  AllIcons.RunConfigurations.TestState.Run,
                  ExecutorAction.getActions(),
                  (element) -> "Run action(s) for " + element.getText());
            }
          }
        }
      }
    }
    return null;
  }
}
