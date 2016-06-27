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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import org.jetbrains.annotations.NotNull;

/**
 * Allows users to set the rule classes they want to be imported
 */
public class AdditionalLanguagesSection {
  public static final SectionKey<LanguageClass, ListSection<LanguageClass>> KEY = SectionKey.of("additional_languages");
  public static final SectionParser PARSER = new AdditionalLanguagesSectionParser();

  private static class AdditionalLanguagesSectionParser extends ListSectionParser<LanguageClass> {
    public AdditionalLanguagesSectionParser() {
      super(KEY);
    }

    @Override
    protected void parseItem(@NotNull ProjectViewParser parser,
                             @NotNull ParseContext parseContext,
                             @NotNull ImmutableList.Builder<LanguageClass> items) {
      String text = parseContext.current().text;
      LanguageClass language = LanguageClass.fromString(text);
      if (language == null) {
        parseContext.addError("Invalid language: " + text);
        return;
      }
      items.add(language);
    }

    @Override
    protected void printItem(@NotNull LanguageClass item, @NotNull StringBuilder sb) {
      sb.append(item.getName());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }
  }
}
