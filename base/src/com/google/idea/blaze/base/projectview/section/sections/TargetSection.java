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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;

/** "targets" section. */
public class TargetSection {
  public static final SectionKey<TargetExpression, ListSection<TargetExpression>> KEY =
      SectionKey.of("targets");
  public static final SectionParser PARSER = new TargetSectionParser();

  private static class TargetSectionParser extends ListSectionParser<TargetExpression> {
    public TargetSectionParser() {
      super(KEY);
    }

    @Override
    protected TargetExpression parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String text = parseContext.current().text;
      return TargetExpression.fromString(text);
    }

    @Override
    protected void printItem(TargetExpression item, StringBuilder sb) {
      sb.append(item.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Label;
    }
  }
}
