package com.google.idea.blaze.base.lang.buildfile.psi;

import com.intellij.lang.ASTNode;

public class FloatLiteral extends BuildElementImpl implements LiteralExpression {

  public FloatLiteral(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitFloatLiteral(this);
  }
}
