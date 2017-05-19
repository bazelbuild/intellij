/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.sharding;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Collects the blaze targets output by 'blaze query --output label_kind "targets"' */
class QueryResultLineProcessor implements LineProcessingOutputStream.LineProcessor {

  static class RuleTypeAndLabel {
    final String ruleType;
    final String label;

    private RuleTypeAndLabel(String ruleType, String label) {
      this.ruleType = ruleType;
      this.label = label;
    }
  }

  private static final Pattern RULE_PATTERN = Pattern.compile("^([^\\s]*) rule ([^\\s]*)$");

  private ImmutableList.Builder<TargetExpression> outputList;
  private final Predicate<RuleTypeAndLabel> targetFilter;

  /**
   * @param outputList Parsed target expressions are added to this list
   * @param targetFilter Ignore targets failing this predicate.
   */
  QueryResultLineProcessor(
      ImmutableList.Builder<TargetExpression> outputList,
      Predicate<RuleTypeAndLabel> targetFilter) {
    this.outputList = outputList;
    this.targetFilter = targetFilter;
  }

  @Override
  public boolean processLine(String line) {
    Matcher match = RULE_PATTERN.matcher(line);
    if (!match.find()) {
      return true;
    }
    String ruleType = match.group(1);
    String label = match.group(2);
    if (targetFilter.test(new RuleTypeAndLabel(ruleType, label))) {
      outputList.add(TargetExpression.fromString(label));
    }
    return true;
  }
}
