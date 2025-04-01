/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.codegenerator;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Some languages have special handling to allow some Targets with a specific Rule name to be
 * processed as a code-generator. This class provides helper functionality related to this
 * mechanism.
 */

public class CodeGeneratorRuleNameHelper {

  /**
   * Because the rule names are passed through string arguments and will be separated with commas,
   * it is important that the provided rule names are not going to clash with these mechanisms. This
   * {@link Pattern} can be used to make a crude check on the rule names' format to be sure of
   * assumptions with downstream processing.
   */

  private final static Pattern PATTERN_RULE_NAME = Pattern.compile("^[a-zA-Z0-9_-]+$");

  /**
   * @return true if the provided <code>ruleName</code> is well-formed.
   */

  public static boolean isValidRuleName(String ruleName) {
    return null != ruleName && PATTERN_RULE_NAME.matcher(ruleName).matches();
  }

  /**
   * This method will produce a list of Rule names that should be assumed to be
   * code generators. It does this in consideration of the provided <code>languageClass</code>.
   */

  public static List<String> deriveRuleNames(ProjectViewSet viewSet, LanguageClass languageClass) {
    return Arrays.stream(BlazeSyncPlugin.EP_NAME.getExtensions())
        .flatMap(plugin -> plugin.getCodeGeneratorRuleNames(viewSet, languageClass).stream())
        .collect(Collectors.toUnmodifiableList());
  }

}
