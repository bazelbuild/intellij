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
