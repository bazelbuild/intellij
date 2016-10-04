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
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

/** Heuristic to match test rules to source files. */
public interface TestRuleHeuristic {

  ExtensionPointName<TestRuleHeuristic> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TestRuleHeuristic");

  /**
   * Given a source file and all test rules reachable from that file, chooses a test rule based on
   * available filters, falling back to choosing the first one if there is no match.
   */
  @Nullable
  static Label chooseTestTargetForSourceFile(
      File sourceFile, Collection<RuleIdeInfo> rules, @Nullable TestIdeInfo.TestSize testSize) {

    for (TestRuleHeuristic filter : EP_NAME.getExtensions()) {
      RuleIdeInfo match =
          rules
              .stream()
              .filter(rule -> filter.matchesSource(rule, sourceFile, testSize))
              .findFirst()
              .orElse(null);

      if (match != null) {
        return match.label;
      }
    }
    return rules.isEmpty() ? null : rules.iterator().next().label;
  }

  /** Returns true if the rule and source file match, according to this heuristic. */
  boolean matchesSource(RuleIdeInfo rule, File sourceFile, @Nullable TestIdeInfo.TestSize testSize);
}
