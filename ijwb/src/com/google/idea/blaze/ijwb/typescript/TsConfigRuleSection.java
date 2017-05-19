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
package com.google.idea.blaze.ijwb.typescript;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import java.util.List;
import javax.annotation.Nullable;

/** Points to the ts_config rule. */
@Deprecated
public class TsConfigRuleSection {
  public static final SectionKey<Label, ScalarSection<Label>> KEY = SectionKey.of("ts_config_rule");
  public static final SectionParser PARSER = new TsConfigRuleSectionParser();

  private static class TsConfigRuleSectionParser extends ScalarSectionParser<Label> {
    public TsConfigRuleSectionParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected Label parseItem(ProjectViewParser parser, ParseContext parseContext, String rest) {
      List<BlazeValidationError> errors = Lists.newArrayList();
      if (!Label.validate(rest, errors)) {
        parseContext.addErrors(errors);
        return null;
      }
      return Label.create(rest);
    }

    @Override
    protected void printItem(StringBuilder sb, Label value) {
      sb.append(value.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Label;
    }

    @Override
    public boolean isDeprecated() {
      return true;
    }

    @Nullable
    @Override
    public String getDeprecationMessage() {
      return "Use `ts_config_rules` instead, which allows specifying multiple `ts_config` targets.";
    }
  }
}
