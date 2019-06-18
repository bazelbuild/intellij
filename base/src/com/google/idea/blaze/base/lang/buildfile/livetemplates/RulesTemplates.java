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
package com.google.idea.blaze.base.lang.buildfile.livetemplates;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import java.util.Optional;

/** Class that provides templates for some build rules. */
public final class RulesTemplates {

  private RulesTemplates() {}

  /** Returns template for a given rule name if available. */
  public static Optional<Template> templateForRule(String ruleName, BuildLanguageSpec spec) {
    RuleDefinition ruleDef = spec.getRule(ruleName);
    if (ruleDef == null || ruleDef.getMandatoryAttributes().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(allMandatoryVariablesTemplate(ruleDef));
  }

  private static Template allMandatoryVariablesTemplate(RuleDefinition ruleDef) {
    TemplateImpl template = new TemplateImpl("", "");
    template.addTextSegment("(");
    ruleDef
        .getMandatoryAttributes()
        .forEach(
            (a, d) -> {
              if (d.getType() == Build.Attribute.Discriminator.STRING) {
                template.addTextSegment("\n    " + a + " = \"");
                addVariableToTemplate(template, a);
                template.addTextSegment("\",");
              } else {
                template.addTextSegment("\n    " + a + " = ");
                addVariableToTemplate(template, a);
                template.addTextSegment(",");
              }
            });
    template.addEndVariable();
    template.addTextSegment("\n)");
    return template;
  }

  private static void addVariableToTemplate(TemplateImpl template, String variableName) {
    template.addVariable(
        variableName,
        /* expression= */ null,
        /* defaultValueExpression= */ null,
        /* isAlwaysStopAt= */ true,
        /* skipOnStart= */ false);
  }
}
