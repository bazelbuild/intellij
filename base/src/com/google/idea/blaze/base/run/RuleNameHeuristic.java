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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo.TestSize;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import javax.annotation.Nullable;

/** Looks for a test rule with rule name matching the source file. */
public class RuleNameHeuristic implements TestRuleHeuristic {

  @Override
  public boolean matchesSource(RuleIdeInfo rule, File sourceFile, @Nullable TestSize testSize) {
    String filePathWithoutExtension = FileUtil.getNameWithoutExtension(sourceFile.getPath());
    String ruleName = rule.label.ruleName().toString();
    if (!filePathWithoutExtension.endsWith(ruleName)) {
      return false;
    }
    int i = filePathWithoutExtension.length() - ruleName.length() - 1;
    if (i < 0) {
      // Equal length
      return true;
    }
    return filePathWithoutExtension.charAt(i) == '/';
  }
}
