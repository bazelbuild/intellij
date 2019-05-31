/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript.run.producers;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Matches the javascript test (e.g., jsunit_test) corresponding to a .js file instead of web_tests.
 */
class JavascriptTestTargetHeuristic implements TestTargetHeuristic {
  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    Kind kind = target.getKind();
    return sourcePsiFile instanceof JSFile
        && kind != null
        && kind.getLanguageClass() == LanguageClass.JAVASCRIPT
        && kind.getRuleType() == RuleType.TEST;
  }
}
