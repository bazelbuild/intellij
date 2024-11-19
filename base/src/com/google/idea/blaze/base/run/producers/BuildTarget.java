package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;

import javax.annotation.Nullable;

record BuildTarget(FuncallExpression rule, RuleType ruleType, Label label) {

  @Nullable
  TargetInfo guessTargetInfo() {
    String ruleName = rule.getFunctionName();
    if (ruleName == null) {
      return null;
    }
    Kind kind = Kind.fromRuleName(ruleName);
    return kind != null ? TargetInfo.builder(label, kind.getKindString()).build() : null;
  }
}
